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

package org.metawidget.faces.component.html.widgetprocessor;

import static org.metawidget.inspector.InspectionResultConstants.*;

import java.util.List;
import java.util.Map;

import javax.faces.application.Application;
import javax.faces.component.UIComponent;
import javax.faces.component.UIInput;
import javax.faces.component.html.HtmlInputHidden;
import javax.faces.context.FacesContext;

import org.metawidget.faces.component.UIMetawidget;
import org.metawidget.faces.component.UIStub;
import org.metawidget.widgetprocessor.iface.WidgetProcessor;

/**
 * WidgetProcessor that adds HTML <code>&lt;input type="hidden"&gt;</code> tags to hidden and
 * read-only values, so that they POST back.
 * <p>
 * Note: passing values via hidden tags is a potential security risk: they can be modified by
 * malicious clients before being returned to the server. Where possible, prefer session-scoped or
 * conversation-scoped beans instead.
 * <p>
 * Note: because this processor relies on StandardBindingProcessor to configure the hidden field's
 * binding, it must come <em>before</em> StandardBindingProcessor in the list of WidgetProcessors.
 *
 * @author <a href="http://kennardconsulting.com">Richard Kennard</a>
 */

public class HiddenFieldProcessor
	implements WidgetProcessor<UIComponent, UIMetawidget> {

	//
	// Public methods
	//

	public UIComponent processWidget( UIComponent component, String elementName, Map<String, String> attributes, UIMetawidget metawidget ) {

		// Ignore actions

		if ( ACTION.equals( elementName ) ) {
			return component;
		}

		// UIInputs do not need a hidden field (they already POST back)

		if ( component instanceof UIInput ) {
			return component;
		}

		// Ignore nested Metawidgets

		if ( component instanceof UIMetawidget ) {
			return component;
		}

		// Attributes without setters cannot use a hidden field

		if ( TRUE.equals( attributes.get( NO_SETTER ) ) ) {
			return component;
		}

		// Ignore manually overridden components

		if ( component.getAttributes().containsKey( UIMetawidget.COMPONENT_ATTRIBUTE_NOT_RECREATABLE ) ) {
			return component;
		}

		return wrapWithHiddenField( component, attributes, metawidget );
	}

	//
	// Protected methods
	//

	/**
	 * @param attributes
	 *            attributes of the widget. Never null
	 * @param metawidget
	 *            the parent Metawidget. May be useful because the component hasn't been added to
	 *            its parent yet
	 */

	protected UIComponent wrapWithHiddenField( UIComponent component, Map<String, String> attributes, UIMetawidget metawidget ) {

		Application application = FacesContext.getCurrentInstance().getApplication();

		if ( component instanceof UIStub ) {
			// Empty stubs become hidden fields directly

			if ( component.getChildCount() == 0 ) {
				return application.createComponent( HtmlInputHidden.COMPONENT_TYPE );
			}

			// We cannot say whether non-empty Stubs will POST-back by themselves (unless we
			// recursed them)

			return component;
		}

		// Other components get wrapped in a Stub

		UIComponent componentStub = application.createComponent( UIStub.COMPONENT_TYPE );

		List<UIComponent> children = componentStub.getChildren();

		children.add( application.createComponent( HtmlInputHidden.COMPONENT_TYPE ) );
		children.add( component );

		return componentStub;
	}
}
