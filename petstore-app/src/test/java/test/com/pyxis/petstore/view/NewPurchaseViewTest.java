package test.com.pyxis.petstore.view;

import com.pyxis.petstore.domain.billing.CreditCardDetails;
import com.pyxis.petstore.domain.billing.CreditCardType;
import org.hamcrest.Matcher;
import org.junit.Before;
import org.junit.Test;
import org.springframework.validation.BeanPropertyBindingResult;
import org.w3c.dom.Element;
import test.support.com.pyxis.petstore.builders.AddressBuilder;
import test.support.com.pyxis.petstore.views.MockErrors;
import test.support.com.pyxis.petstore.views.ModelBuilder;
import test.support.com.pyxis.petstore.views.VelocityRendering;

import java.util.ArrayList;
import java.util.List;

import static com.pyxis.matchers.dom.DomMatchers.*;
import static com.pyxis.petstore.domain.billing.CreditCardType.mastercard;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static test.support.com.pyxis.petstore.builders.AddressBuilder.anAddress;
import static test.support.com.pyxis.petstore.builders.CartBuilder.aCart;
import static test.support.com.pyxis.petstore.builders.CreditCardBuilder.aMasterCard;
import static test.support.com.pyxis.petstore.builders.ItemBuilder.anItem;
import static test.support.com.pyxis.petstore.views.MockErrors.errorsOn;
import static test.support.com.pyxis.petstore.views.ModelBuilder.aModel;
import static test.support.com.pyxis.petstore.views.PathFor.purchasesPath;
import static test.support.com.pyxis.petstore.views.VelocityRendering.render;

@SuppressWarnings("unchecked")
public class NewPurchaseViewTest {

    String NEW_PURCHASE_VIEW_NAME = "purchases/new";
    Element newPurchaseView;
    ModelBuilder model;

    @Before public void
    renderView() {
        model = aModel().
                with(aCart().containing(anItem().priced("100.00"))).
                and("cardTypes", CreditCardType.options());
        newPurchaseView = renderNewPurchaseView().using(model).asDom();
    }

    @Test public void
    displaysOrderSummary() {
        assertThat("view", newPurchaseView, hasUniqueSelector("#cart-grand-total", withText("100.00")));
    }

	@Test public void
    displaysPurchaseForm() {
        assertThat("view", newPurchaseView, checkoutForm(
                    withAttribute("action", purchasesPath()),
                    withAttribute("method", "post")
                ));
        assertThat("view", newPurchaseView, checkoutForm(withEmptyBillingInformation()));
        assertThat("view", newPurchaseView, checkoutForm(withPaymentDetails()));
        assertThat("view", newPurchaseView, checkoutForm(withSubmitOrderButton()));
    }

    private Matcher<? super Element> checkoutForm(Matcher<Element>... elementMatchers) {
        return hasUniqueSelector("form#order-form", elementMatchers);
    }

    @Test public void
    fillsCardTypeSelectionList() {
        assertThat("view", newPurchaseView, hasSelector("#card-type option", withCreditCardOptions()));
    }

    @Test public void
    rendersErrorsWhenPaymentDetailsAreInvalid() {
        MockErrors errors = errorsOn("paymentDetails");
        errors.reject("invalid");
        errors.rejectValue("cardNumber", "empty");
        newPurchaseView = renderNewPurchaseView().using(model).bind(errors).asDom();

        assertThat("view", newPurchaseView, hasUniqueSelector("#payment-details-errors", hasChild(
                withText("invalid.paymentDetails")
        )));
        assertThat("view", newPurchaseView, hasUniqueSelector("#card-number-errors", hasChild(
                withText("empty.paymentDetails.cardNumber")
        )));
    }

	@Test public void
    restoresFormValuesWhenAValidationErrorOccurs() {
    	AddressBuilder billingAddress = anAddress().
                withName("Jack", "Johnson").withEmail("jack@gmail.com");
		CreditCardDetails creditCardDetails = aMasterCard().
                withNumber("1111 2222 3333 4444").
                withExpiryDate("2010-10-10").
                billedTo(billingAddress).build();
    	newPurchaseView = renderNewPurchaseView().using(model.with("paymentDetails", creditCardDetails)).bind(validationErrorsOn("paymentDetails", creditCardDetails)).asDom();
		assertThat("view", newPurchaseView, checkoutForm(
    			withBillingInformation("Jack", "Johnson", "jack@gmail.com"), 
    			withCreditCardDetails(mastercard, "1111 2222 3333 4444", "2010-10-10")));
    }

    private BeanPropertyBindingResult validationErrorsOn(String objectName, CreditCardDetails creditCardDetails) {
		return new BeanPropertyBindingResult(creditCardDetails, objectName);
	}
    
	private Matcher<Element> withCreditCardDetails(CreditCardType cardType, String cardNumber, String cardExpiryDate) {
		return allOf( 
				withCardNumberAndExpiryDate(cardNumber, cardExpiryDate),
	            withSelectedCardType(cardType));
	}

	private Matcher<Element> withSelectedCardType(CreditCardType cardType) {
		return withSelectionLists(
			allOf(withName("cardType"), hasChild(allOf(withAttribute("value", cardType.toString()), withAttribute("selected", "selected")))));
	}

	private Matcher<Element> withCardNumberAndExpiryDate(String cardNumber, String cardExpiryDate) {
		return withInputFields(
		    allOf(withName("cardNumber"), withAttribute("value", cardNumber)),
		    allOf(withName("cardExpiryDate"), withAttribute("value", cardExpiryDate)));
	}

	private Matcher<Element> withBillingInformation(String firstName, String lastName, String email) {
		return withInputFields(
                allOf(withName("billingAddress.firstName"), withAttribute("value", firstName)),
                allOf(withName("billingAddress.lastName"), withAttribute("value", lastName)),
                allOf(withName("billingAddress.emailAddress"), withAttribute("value", email)));
	}

	private Matcher<Element> withEmptyBillingInformation() {
        return withBillingInformation("", "", "");
    }

    private Matcher<Element> withPaymentDetails() {
        return allOf(
                withSelectionLists(withName("cardType")),
                withEmptyCardNumberAndExpiryDate()
        );
    }

	private Matcher<Element> withEmptyCardNumberAndExpiryDate() {
		return withCardNumberAndExpiryDate("", "");
	}

    private Matcher<Element> withSelectionLists(final Matcher<Element>... dropDownMatchers) {
        return hasSelector("select", dropDownMatchers);
    }

    private Matcher<Element> withSubmitOrderButton() {
        return hasUniqueSelector("#order");
    }

    private Matcher<Iterable<Element>> withCreditCardOptions() {
        List<Matcher<? super Element>> matchers = new ArrayList<Matcher<? super Element>>();
        for (CreditCardType type : CreditCardType.values()) {
            matchers.add(withOption(type.name(), type.commonName()));
        }
        return containsInAnyOrder(matchers);
    }

    private Matcher<Element> withOption(String value, String text) {
        return allOf(withAttribute("value", value), withText(text));
    }

    private Matcher<Element> withInputFields(final Matcher<Element>... fieldMatchers) {
        return hasSelector("input[type='text']", fieldMatchers);
    }

    private VelocityRendering renderNewPurchaseView() {
        return render(NEW_PURCHASE_VIEW_NAME);
    }
}
