package	net.sourceforge.solexatools.validation;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.sourceforge.seqware.common.model.RegistrationDTO;

import org.springframework.validation.Errors;
import org.springframework.validation.ValidationUtils;
import org.springframework.validation.Validator;

/**
 * <p>LoginValidator class.</p>
 *
 * @author boconnor
 * @version $Id: $Id
 */
public class LoginValidator implements Validator {
	/** Constant <code>CONST_AT_SIGN="@"</code> */
	protected static final String CONST_AT_SIGN = "@";

	/**
	 * <p>Constructor for LoginValidator.</p>
	 */
	public LoginValidator() {
		super();
	}

	/**
	 * {@inheritDoc}
	 *
	 * Returns true if this Validator supports the
	 * specified Class, and false otherwise.
	 */
	public boolean supports(Class clazz) {
		return RegistrationDTO.class.equals(clazz);
	}

	/**
	 * {@inheritDoc}
	 *
	 * Validates the specified Object.
	 */
	public void validate(Object obj, Errors errors) {
		RegistrationDTO registration = (RegistrationDTO) obj;
		this.validateEmail("emailAddress", registration.getEmailAddress(), errors);
		ValidationUtils.rejectIfEmpty(errors, "password", "required.password");
	}

	/**
	 * Validates an email address.
	 *
	 * @param emailProperty email property name such as "emailAddress"
	 * @param emailValue value of the email property
	 * @param errors Errors object for validation errors
	 */
	public void validateEmail(String emailProperty, String emailValue, Errors errors) {
		//if (emailValue == null || emailValue.indexOf(CONST_AT_SIGN) == -1) {
		//	errors.rejectValue(emailProperty, "required." + emailProperty);
		//}
		
		if (emailValue == null || !isCheckEmail(emailValue)) {
			errors.rejectValue(emailProperty, "required." + emailProperty);
		}
	}
	
	/**
	 * <p>isCheckEmail.</p>
	 *
	 * @param emailValue a {@link java.lang.String} object.
	 * @return a boolean.
	 */
	public static boolean isCheckEmail(String emailValue){
		boolean isValid = false;
		if (emailValue != null ) {
		    Pattern p = Pattern.compile(".+@.+\\.[a-z]+");
		    Matcher m = p.matcher(emailValue);
		    boolean matchFound = m.matches();

		    if(matchFound){
		    	isValid = true;
		    }else{
		    	isValid = false;
		    }
		}
		return isValid;
	}
		
}

// ex:sw=4:ts=4:
