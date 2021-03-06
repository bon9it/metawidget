// Metawidget
//
// This file is dual licensed under both the LGPL
// (http://www.gnu.org/licenses/lgpl-2.1.html) and the EPL
// (http://www.eclipse.org/org/documents/epl-v10.php). As a
// recipient of Metawidget, you may choose to receive it under either
// the LGPL or the EPL.
//
// Commercial licenses are also available. See http://metawidget.org
// for details.

package org.metawidget.inspectionresultprocessor.commons.jexl;

import java.util.Collection;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.jexl2.JexlContext;
import org.apache.commons.jexl2.JexlEngine;
import org.apache.commons.jexl2.MapContext;
import org.metawidget.inspectionresultprocessor.iface.InspectionResultProcessorException;
import org.metawidget.inspectionresultprocessor.impl.BaseInspectionResultProcessor;
import org.metawidget.inspector.impl.propertystyle.PropertyStyle;
import org.metawidget.util.ArrayUtils;
import org.metawidget.util.CollectionUtils;
import org.metawidget.util.simple.StringUtils;
import org.w3c.dom.Element;

/**
 * Processes the inspection result and evaluates any expressions of the form <code>${...}</code>
 * using JEXL.
 *
 * @author <a href="http://kennardconsulting.com">Richard Kennard</a>
 */

public class JexlInspectionResultProcessor<M>
	extends BaseInspectionResultProcessor<M> {

	//
	// Private statics
	//

	private static final String						THIS_ATTRIBUTE		= "this";

	private static final ThreadLocal<JexlContext>	LOCAL_CONTEXT		= new ThreadLocal<JexlContext>();

	private static final Pattern					PATTERN_EXPRESSION	= Pattern.compile( "\\$\\{([^\\}]+)\\}" );

	//
	// Private members
	//

	private PropertyStyle							mInjectThis;

	private Object[]								mInject;

	private JexlEngine								mJexlEngine;

	//
	// Constructors
	//

	/**
	 * Constructs a FacesInspectionResultProcessor.
	 */

	public JexlInspectionResultProcessor() {

		this( new JexlInspectionResultProcessorConfig() );
	}

	/**
	 * Constructs a FacesInspectionResultProcessor.
	 */

	public JexlInspectionResultProcessor( JexlInspectionResultProcessorConfig config ) {

		mInjectThis = config.getInjectThis();
		mInject = config.getInject();
		mJexlEngine = createEngine();
	}

	@Override
	public Element processInspectionResultAsDom( Element inspectionResult, M metawidget, Object toInspect, String type, String... names ) {

		try {
			LOCAL_CONTEXT.set( createContext( metawidget ) );
			return super.processInspectionResultAsDom( inspectionResult, metawidget, toInspect, type, names );

		} finally {
			LOCAL_CONTEXT.remove();
		}
	}

	//
	// Protected methods
	//

	@Override
	protected void processEntity( Map<String, String> attributes, M metawidget, Object toInspect, String type, String... names ) {

		JexlContext context = LOCAL_CONTEXT.get();

		try {
			context.set( THIS_ATTRIBUTE, mInjectThis.traverse( toInspect, type, true, names ).getValue() );
			super.processEntity( attributes, metawidget, toInspect, type, names );

		} finally {

			// THIS_ATTRIBUTE should not be available outside of our particular evaluation

			context.set( THIS_ATTRIBUTE, null );
		}
	}

	@Override
	protected void processTraits( Element entity, M metawidget, Object toInspect, String type, String... names ) {

		JexlContext context = LOCAL_CONTEXT.get();

		try {
			context.set( THIS_ATTRIBUTE, mInjectThis.traverse( toInspect, type, false, names ).getValue() );
			super.processTraits( entity, metawidget, toInspect, type, names );

		} finally {

			// THIS_ATTRIBUTE should not be available outside of our particular evaluation

			context.set( THIS_ATTRIBUTE, null );
		}
	}

	@Override
	protected void processAttributes( Map<String, String> attributes, M metawidget ) {

		// For each attribute value...

		for ( Map.Entry<String, String> entry : attributes.entrySet() ) {

			String key = entry.getKey();
			String value = entry.getValue();

			// ...that contains an EL expression...

			Matcher matcher = PATTERN_EXPRESSION.matcher( value );
			int matchOffset = 0;

			while ( matcher.find() ) {

				String expression = matcher.group( 1 );

				// ...evaluate it...

				try {
					Object valueObject = mJexlEngine.createExpression( expression ).evaluate( LOCAL_CONTEXT.get() );
					String valueObjectAsString;

					if ( valueObject == null ) {

						// Support the default case (when the String is just one EL)

						if ( matcher.start() == 0 && matcher.end() == value.length() ) {
							value = null;
							break;
						}

						valueObjectAsString = "";
					} else {

						// Support the default case (when the String is just one EL)

						if ( matcher.start() == 0 && matcher.end() == value.length() ) {
							if ( valueObject instanceof Collection<?> ) {
								value = CollectionUtils.toString( (Collection<?>) valueObject );
							} else if ( valueObject.getClass().isArray() ) {
								value = ArrayUtils.toString( valueObject );
							} else {
								value = String.valueOf( valueObject );
							}
							break;
						}

						valueObjectAsString = String.valueOf( valueObject );
					}

					// Replace multiple ELs within the String

					value = new StringBuilder( value ).replace( matcher.start() + matchOffset, matcher.end() + matchOffset, valueObjectAsString ).toString();
					matchOffset += valueObjectAsString.length() - ( matcher.end() - matcher.start() );

				} catch ( Exception e ) {

					// We have found it helpful to include the actual expression we were trying to
					// evaluate

					throw InspectionResultProcessorException.newException( "Unable to evaluate " + value, e );
				}
			}

			// ...and replace it

			attributes.put( key, value );
		}
	}

	/**
	 * Prepare the JexlEngine.
	 */

	protected JexlEngine createEngine() {

		JexlEngine engine = new JexlEngine();
		
		// Suppress warning 'inaccessible or unknown property this' from
		// 'createExpression' (which is fine once we call 'evaluate' and pass it
		// a local context)

		engine.setSilent( true );
		return engine;
	}

	/**
	 * Prepare the JexlContext. This includes injecting any Objects passed by
	 * <code>JexlInspectionResultProcessor.setInject</code>.
	 * <p>
	 * Subclasses can override this method to control what is available in the context.
	 *
	 * @param metawidget
	 *            the parent Metawidget. Never null. May be useful for finding the object to add to
	 *            the context
	 */

	protected JexlContext createContext( M metawidget ) {

		JexlContext context = new MapContext();

		if ( mInject != null ) {

			for ( Object inject : mInject ) {
				context.set( StringUtils.decapitalize( inject.getClass().getSimpleName() ), inject );
			}
		}

		return context;
	}
}
