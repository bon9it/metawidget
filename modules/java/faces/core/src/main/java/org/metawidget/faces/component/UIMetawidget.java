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

package org.metawidget.faces.component;

import static org.metawidget.inspector.InspectionResultConstants.*;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import javax.faces.application.Application;
import javax.faces.component.EditableValueHolder;
import javax.faces.component.UIComponent;
import javax.faces.component.UIInput;
import javax.faces.component.UIParameter;
import javax.faces.component.UIViewRoot;
import javax.faces.component.ValueHolder;
import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;
import javax.faces.context.PartialViewContext;
import javax.faces.el.ValueBinding;
import javax.faces.event.AbortProcessingException;
import javax.faces.event.PreRenderViewEvent;
import javax.faces.event.SystemEvent;
import javax.faces.event.SystemEventListener;
import javax.faces.validator.Validator;

import org.metawidget.config.iface.ConfigReader;
import org.metawidget.config.impl.BaseConfigReader;
import org.metawidget.faces.FacesUtils;
import org.metawidget.iface.MetawidgetException;
import org.metawidget.inspectionresultprocessor.iface.InspectionResultProcessor;
import org.metawidget.inspector.iface.Inspector;
import org.metawidget.layout.iface.Layout;
import org.metawidget.pipeline.w3c.W3CPipeline;
import org.metawidget.util.ClassUtils;
import org.metawidget.util.CollectionUtils;
import org.metawidget.util.LogUtils;
import org.metawidget.util.LogUtils.Log;
import org.metawidget.util.WidgetBuilderUtils;
import org.metawidget.util.XmlUtils;
import org.metawidget.util.simple.StringUtils;
import org.metawidget.widgetbuilder.iface.WidgetBuilder;
import org.metawidget.widgetprocessor.iface.WidgetProcessor;
import org.w3c.dom.Element;

/**
 * Base Metawidget for Java Server Faces environments.
 * <p>
 * Its default RendererType is <code>table</code>.
 * <p>
 * <h2>Resolving Directly To A Single Widget</h2>
 * <p>
 * If the entire Metawidget resolves directly to a single widget, Metawidget allows you to attach
 * converters, facets and validators to the dynamically chosen component. Tags placed inside the
 * Metawidget tag will be moved on to the generated component. For example:
 * <p>
 * <code>&lt;m:metawidget value="#{user.name}"&gt;<br/>
 * &nbsp;&nbsp;&nbsp;&lt;f:validator validatorId="myValidator"&gt;<br/>
 * &lt;/m:metawidget&gt;</code>
 * <p>
 * Conceptually, <code>UIMetawidget</code> should only extend <code>UIComponentBase</code>. This is
 * because it is not:
 * <p>
 * <ul>
 * <li>a <code>UIInput</code>, though it may contain input widgets
 * <li>a <code>UIOutput</code>, though it may contain output widgets
 * <li>a <code>ValueHolder</code>, as it does not use a <code>Converter</code>
 * <li>an <code>EditableValueHolder</code>, as it does not use a <code>Validator</code>
 * </ul>
 * <p>
 * However by extending <code>UIInput</code>, we enable this useful 'attach facets to a single
 * widget' capability.
 * 
 * @author <a href="http://kennardconsulting.com">Richard Kennard</a>
 */

@SuppressWarnings( "deprecation" )
public abstract class UIMetawidget
	extends UIInput {

	//
	// Public statics
	//

	/**
	 * Component-level attribute used to store metadata.
	 */

	public static final String				COMPONENT_ATTRIBUTE_METADATA			= "metawidget-metadata";

	/**
	 * Component-level attribute used to prevent recreation.
	 * <p>
	 * By default, Metawidget destroys and recreates every component after
	 * <code>processUpdates</code> and before <code>encodeBegin</code>. This allows components to
	 * update to reflect changed state in underlying domain objects. For example, components may
	 * change from being <code>UIOutput</code> labels to <code>UIInput</code> text boxes after the
	 * user clicks <code>Edit</code>.
	 * <p>
	 * Most components work well with this approach. Some, however, maintain internal state that
	 * would get lost if the component was destroyed and recreated. For example, the ICEfaces
	 * <code>SelectInputDate</code> component keeps its popup state internally. If it is destroyed
	 * and recreated, the popup never appears.
	 * <p>
	 * Such components can be marked with <code>COMPONENT_ATTRIBUTE_NOT_RECREATABLE</code> to
	 * prevent their destruction and recreation. Of course, this somewhat impacts their flexibility.
	 * For example, a <code>SelectInputDate</code> could not change its date format in response to
	 * another component on the form.
	 * <p>
	 * <code>COMPONENT_ATTRIBUTE_NOT_RECREATABLE</code> is also used to mark components that
	 * override default component generation, such as a <code>UIStub</code> used to suppress a
	 * field.
	 * <p>
	 * This attribute must be used in conjunction with <code>OverriddenWidgetBuilder</code>.
	 */

	public static final String				COMPONENT_ATTRIBUTE_NOT_RECREATABLE		= "metawidget-not-recreatable";

	/**
	 * Component-level attribute used to store metadata.
	 */

	public static final String				COMPONENT_ATTRIBUTE_SECTION_DECORATOR	= "metawidget-section-decorator";

	//
	// Private statics
	//

	/**
	 * Application-level attribute used to cache ConfigReader. This can also be used to inject a
	 * different ConfigReader if needed (ie. for Grails)
	 */

	private static final String				APPLICATION_ATTRIBUTE_CONFIG_READER		= "metawidget-config-reader";

	private static final String				DEFAULT_USER_CONFIG						= "metawidget.xml";

	private static final String				COMPONENT_ATTRIBUTE_PARAMETER_PREFIX	= "metawidget-parameter-";

	/* package private */static final Log	LOG										= LogUtils.getLog( UIMetawidget.class );

	/* package private */static boolean		LOGGED_MISSING_CONFIG;

	private static Boolean					USE_PRERENDER_VIEW_EVENT;

	/**
	 * The standard component family for this component.
	 */

	@SuppressWarnings( "hiding" )
	private static final String				COMPONENT_FAMILY						= "org.metawidget";

	//
	// Private members
	//

	/* package private */boolean			mExplicitRendererType;

	/* package private */boolean			mBuildWidgetsOnAjaxRequest;

	private boolean							mInspectFromParent;

	private boolean							mReadOnly;

	private Map<Object, Object>				mClientProperties;

	/* package private */Pipeline			mPipeline;

	/* package private */Object				mBuildWidgetsSupport;

	//
	// Constructor
	//

	public UIMetawidget() {

		mPipeline = newPipeline();

		// Default config

		FacesContext context = FacesContext.getCurrentInstance();
		ExternalContext externalContext = context.getExternalContext();

		String configFile = externalContext.getInitParameter( COMPONENT_FAMILY + ".faces.component.CONFIG_FILE" );

		if ( configFile == null ) {
			setConfig( DEFAULT_USER_CONFIG );
		} else {
			setConfig( configFile );
		}

		FacesContext facesContext = UIMetawidget.this.getFacesContext();
		Map<String, Object> applicationMap = facesContext.getExternalContext().getApplicationMap();
		ConfigReader configReader = (ConfigReader) applicationMap.get( APPLICATION_ATTRIBUTE_CONFIG_READER );

		if ( configReader == null ) {
			configReader = new BaseConfigReader( new FacesResourceResolver() );
			applicationMap.put( APPLICATION_ATTRIBUTE_CONFIG_READER, configReader );
		}

		mPipeline.setConfigReader( configReader );

		// Default renderer (not set mExplicitRendererType yet)

		super.setRendererType( "table" );
		mExplicitRendererType = false;

		registerBuildWidgetsSupport();
	}

	//
	// Public methods
	//

	@Override
	public String getFamily() {

		return COMPONENT_FAMILY;
	}

	public boolean isReadOnly() {

		// Dynamic read-only (takes precedence if set)

		ValueBinding bindingReadOnly = getValueBinding( "readOnly" );

		if ( bindingReadOnly != null ) {
			return (Boolean) bindingReadOnly.getValue( getFacesContext() );
		}

		// Default to read-write

		return mReadOnly;
	}

	public void setReadOnly( boolean readOnly ) {

		mReadOnly = readOnly;
	}

	public void setConfig( String config ) {

		mPipeline.setConfig( config );
	}

	public void setInspector( Inspector inspector ) {

		mPipeline.setInspector( inspector );
	}

	/**
	 * Useful for WidgetBuilders to perform nested inspections (eg. for Collections).
	 */

	public String inspect( Object toInspect, String type, String... names ) {

		return mPipeline.inspect( toInspect, type, names );
	}

	public void addInspectionResultProcessor( InspectionResultProcessor<UIMetawidget> inspectionResultProcessor ) {

		mPipeline.addInspectionResultProcessor( inspectionResultProcessor );
	}

	public void removeInspectionResultProcessor( InspectionResultProcessor<UIMetawidget> inspectionResultProcessor ) {

		mPipeline.removeInspectionResultProcessor( inspectionResultProcessor );
	}

	public void setInspectionResultProcessors( InspectionResultProcessor<UIMetawidget>... inspectionResultProcessors ) {

		mPipeline.setInspectionResultProcessors( inspectionResultProcessors );
	}

	public void setWidgetBuilder( WidgetBuilder<UIComponent, UIMetawidget> widgetBuilder ) {

		mPipeline.setWidgetBuilder( widgetBuilder );
	}

	/**
	 * Exposed mainly for those using <code>UIComponent.setBinding</code>.
	 */

	public WidgetBuilder<UIComponent, UIMetawidget> getWidgetBuilder() {

		return mPipeline.getWidgetBuilder();
	}

	/**
	 * Exposed mainly for those using <code>UIComponent.setBinding</code>.
	 */

	public void addWidgetProcessor( WidgetProcessor<UIComponent, UIMetawidget> widgetProcessor ) {

		mPipeline.addWidgetProcessor( widgetProcessor );
	}

	/**
	 * Exposed mainly for those using <code>UIComponent.setBinding</code>.
	 */

	public void removeWidgetProcessor( WidgetProcessor<UIComponent, UIMetawidget> widgetProcessor ) {

		mPipeline.removeWidgetProcessor( widgetProcessor );
	}

	public void setWidgetProcessors( WidgetProcessor<UIComponent, UIMetawidget>... widgetProcessors ) {

		mPipeline.setWidgetProcessors( widgetProcessors );
	}

	public List<WidgetProcessor<UIComponent, UIMetawidget>> getWidgetProcessors() {

		return mPipeline.getWidgetProcessors();
	}

	public <T> T getWidgetProcessor( Class<T> widgetProcessorClass ) {

		return mPipeline.getWidgetProcessor( widgetProcessorClass );
	}

	public void setLayout( Layout<UIComponent, UIComponent, UIMetawidget> layout ) {

		mPipeline.setLayout( layout );
	}

	public Layout<UIComponent, UIComponent, UIMetawidget> getLayout() {

		return mPipeline.getLayout();
	}

	/**
	 * Instructs the Metawidget to inspect the value binding from the parent.
	 * <p>
	 * If the value binding is of the form <code>#{foo.bar}</code>, sometimes
	 * <code>foo.getBar()</code> has useful metadata (such as <code>UiLookup</code>). Metawidget
	 * inspects from parent anyway if <code>#{foo.bar}</code> evaluates to <code>null</code>, but on
	 * occasion it may be necessary to specify parent inspection explicitly.
	 * <p>
	 * The disadvantage of inspecting from parent (and the reason it is not enabled by default) is
	 * that some Inspectors will not know the parent and so not be able to return anything. For
	 * example, HibernateInspector only knows the Hibernate XML mapping files of persistent classes,
	 * not the business class of a UI controller, so asking HibernateInspector to inspect
	 * <code>#{controller.current}</code> from its parent will always return <code>null</code>.
	 */

	public void setInspectFromParent( boolean inspectFromParent ) {

		mInspectFromParent = inspectFromParent;
	}

	/**
	 * By default, <code>UIMetawidget</code> does not rebuild widgets upon an AJAX request unless
	 * the Metawidget's <code>Id</code> is explicitly included in the list of <code>execute</code>
	 * Ids. There are several reasons for this:
	 * <p>
	 * <ol>
	 * <li>Suppose a Metawidget X has children A, B and C. If B is executed by an AJAX request, this
	 * will trigger X with a <code>PreRenderViewEvent</code> (because it is the parent). But if X
	 * rebuilds A and C, and they <em>weren't</em> part of the execute request, their values will be
	 * lost. This is similar to how <code>UIMetawidget</code> doesn't rebuild upon a validation
	 * error</li>
	 * <li>Similarly, if the Metawidget's backing bean is request-scoped, rebuilding A and C will
	 * mean they fetch their values from a new (likely empty) backing bean instance. There will be
	 * no opportunity for A and C to postback their values first (because they are not executed)</li>
	 * <li>Some components (such as RichFaces' <code>UIAutocomplete</code>) do not allow
	 * fine-grained control over what is executed and rendered. They just execute and render
	 * themselves</li>
	 * <li>AJAX is about performance, so typically clients are not wanting to rebuild large sections
	 * of the component tree</li>
	 * </ol>
	 * <p>
	 * Although this default behaviour is safer it does, however, result in less dynamic UIs.
	 * Clients can use <code>setBuildWidgetsOnAjaxRequest</code> to override the default behaviour
	 * and instruct <code>UIMetawidget</code> to always rebuild widgets upon an AJAX request.
	 * Mechanisms such as conversation-scoped backing beans can be used to avoid losing values.
	 */

	public void setBuildWidgetsOnAjaxRequest( boolean buildWidgetsOnAjaxRequest ) {

		mBuildWidgetsOnAjaxRequest = buildWidgetsOnAjaxRequest;
	}

	/**
	 * Returns a label for the given set of attributes.
	 * <p>
	 * The label is determined using the following algorithm:
	 * <p>
	 * <ul>
	 * <li>if <tt>attributes.get( "label" )</tt> exists...
	 * <ul>
	 * <li><tt>attributes.get( "label" )</tt> is camel-cased and used as a lookup into
	 * <tt>getLocalizedKey( camelCasedLabel )</tt>. This means developers can initially build their
	 * UIs without worrying about localization, then turn it on later</li>
	 * <li>if no such lookup exists, return <tt>attributes.get( "label" )</tt>
	 * </ul>
	 * </li>
	 * <li>if <tt>attributes.get( "label" )</tt> does not exist...
	 * <ul>
	 * <li><tt>attributes.get( "name" )</tt> is used as a lookup into
	 * <tt>getLocalizedKey( name )</tt></li>
	 * <li>if no such lookup exists, return <tt>attributes.get( "name" )</tt>
	 * </ul>
	 * </li>
	 * </ul>
	 * 
	 * @return the text of the label. This may itself contain a value expression, such as
	 *         <code>UiLabel( "#{foo.name}'s name" )</code>
	 */

	public String getLabelString( Map<String, String> attributes ) {

		if ( attributes == null ) {
			return "";
		}

		// Explicit label

		String label = attributes.get( LABEL );

		if ( label != null ) {
			// (may be forced blank)

			if ( "".equals( label ) ) {
				return null;
			}

			// (localize if possible)

			String localized = getLocalizedKey( StringUtils.camelCase( label ) );

			if ( localized != null ) {
				return localized.trim();
			}

			return label.trim();
		}

		// Default name

		String name = attributes.get( NAME );

		if ( name != null ) {
			// (localize if possible)

			String localized = getLocalizedKey( name );

			if ( localized != null ) {
				return localized.trim();
			}

			return StringUtils.uncamelCase( name );
		}

		return "";
	}

	/**
	 * @return null if no bundle, ???key??? if bundle is missing a key
	 */

	public String getLocalizedKey( String key ) {

		String localizedKey = null;
		FacesContext context = FacesContext.getCurrentInstance();
		Application application = context.getApplication();
		String appBundle = application.getMessageBundle();

		// Component-specific bundle

		ValueBinding bindingBundle = getValueBinding( "bundle" );

		if ( bindingBundle != null ) {
			// (watch out when localizing blank labels)

			if ( key == null || key.trim().length() == 0 ) {
				return "";
			}

			@SuppressWarnings( "unchecked" )
			Map<String, String> bundleMap = (Map<String, String>) bindingBundle.getValue( context );

			// (check for containsKey first, because BundleMap will return a dummy value otherwise)

			if ( bundleMap.containsKey( key ) ) {
				localizedKey = bundleMap.get( key );
			}
		} else if ( appBundle != null ) {

			// App-specific bundle

			try {
				localizedKey = ResourceBundle.getBundle( appBundle, context.getViewRoot().getLocale() ).getString( key );
			} catch ( MissingResourceException e ) {
				// Fail gracefully: we seem to have problems locating, say,
				// org.jboss.seam.core.SeamResourceBundle?

				return null;
			}
		} else {

			// No bundle

			return null;
		}

		if ( localizedKey != null ) {
			return localizedKey;
		}

		return StringUtils.RESOURCE_KEY_NOT_FOUND_PREFIX + key + StringUtils.RESOURCE_KEY_NOT_FOUND_SUFFIX;
	}

	/**
	 * Sets the parameter with the given name to the given value.
	 * <p>
	 * This method will not override existing, manually specified <code>&lt;f:param /&gt;</code>.
	 * Rather it will store the given parameter in <code>UIComponent.getAttributes</code>. This has
	 * several advantages:
	 * <p>
	 * <ul>
	 * <li>Creating child UIParameter components for every parameter seems very heavy</li>
	 * <li>Copying parameters to nested Metawidgets can be done using the <code>getAttributes</code>
	 * Map, without needing to create nested UIParameter components</li>
	 * <li>Nested UIParameter components complicate <code>removeRecreatableChildren</code> because
	 * although they are not manually specified, they will not be automatically recreated either</li>
	 * </ul>
	 * <p>
	 * A <em>disadvantage</em> of this approach is that clients should always use
	 * <code>UIMetawidget.getParameter</code> to retrieve parameters, rather than searching for
	 * UIParameter components directly.
	 */

	public void setParameter( String name, Object value ) {

		getAttributes().put( COMPONENT_ATTRIBUTE_PARAMETER_PREFIX + name, value );
	}

	/**
	 * Gets the parameter with the given name.
	 * <p>
	 * As discussed in the <code>setParameter</code> JavaDoc, clients should use this method to
	 * retrieve parameters, rather than searching for UIParameter components directly.
	 */

	public String getParameter( String name ) {

		// Try UIParameters first...

		for ( UIComponent child : getChildren() ) {
			if ( !( child instanceof UIParameter ) ) {
				continue;
			}

			// ...with the name we're interested in

			UIParameter parameter = (UIParameter) child;

			if ( name.equals( parameter.getName() ) ) {
				return (String) parameter.getValue();
			}
		}

		// ...then our internal parameters

		return (String) getAttributes().get( COMPONENT_ATTRIBUTE_PARAMETER_PREFIX + name );
	}

	/**
	 * As discussed in the <code>setParameter</code> JavaDoc, clients should use this method to
	 * copy parameters, rather than creating UIParameter components directly.
	 */

	public void copyParameters( UIMetawidget copyFrom ) {

		// Copy each child UIParameter

		for ( UIComponent component : copyFrom.getChildren() ) {
			if ( !( component instanceof UIParameter ) ) {
				continue;
			}

			UIParameter parameter = (UIParameter) component;
			setParameter( parameter.getName(), parameter.getValue() );
		}

		// Copy each internal parameter too

		for ( Map.Entry<String, Object> entry : copyFrom.getAttributes().entrySet() ) {

			if ( !entry.getKey().startsWith( COMPONENT_ATTRIBUTE_PARAMETER_PREFIX ) ) {
				continue;
			}

			getAttributes().put( entry.getKey(), entry.getValue() );
		}
	}

	/**
	 * Storage area for WidgetProcessors, Layouts, and other stateless clients.
	 * <p>
	 * Unlike <code>.setAttribute</code>, these values are not serialized by
	 * <code>ResponseStateManagerImpl</code>.
	 */

	public void putClientProperty( Object key, Object value ) {

		if ( mClientProperties == null ) {
			mClientProperties = CollectionUtils.newHashMap();
		}

		mClientProperties.put( key, value );
	}

	/**
	 * Storage area for WidgetProcessors, Layouts, and other stateless clients.
	 * <p>
	 * Unlike <code>.getAttribute</code>, these values are not serialized by
	 * <code>ResponseStateManagerImpl</code>.
	 */

	@SuppressWarnings( "unchecked" )
	public <T> T getClientProperty( Object key ) {

		if ( mClientProperties == null ) {
			return null;
		}

		return (T) mClientProperties.get( key );
	}

	@Override
	public boolean isRendered() {

		boolean rendered = super.isRendered();

		if ( mBuildWidgetsSupport instanceof EncodeBeginSupport ) {
			( (EncodeBeginSupport) mBuildWidgetsSupport ).isRendered( rendered );
		}

		return rendered;
	}

	/**
	 * Overridden to flag whether the rendererType has been set explicitly by the page.
	 * <p>
	 * This stops global defaults, if defined in <code>metawidget.xml</code>, from overriding the
	 * page-level renderType. This is because <code>metawidget.xml</code> is not parsed until
	 * <em>after</em> <tt>setRendererType</tt> has been called.
	 */

	@Override
	public void setRendererType( String rendererType ) {

		mExplicitRendererType = true;
		super.setRendererType( rendererType );
	}

	@Override
	public void encodeBegin( FacesContext context )
		throws IOException {

		if ( mBuildWidgetsSupport instanceof EncodeBeginSupport ) {
			( (EncodeBeginSupport) mBuildWidgetsSupport ).encodeBegin();
		}

		super.encodeBegin( context );
	}

	/**
	 * Get the component type used to create this Metawidget.
	 * <p>
	 * Usually, clients will want to create a nested-Metawidget using the same subclass as
	 * themselves. To be 'proper' in JSF, though, we should go via
	 * <code>application.createComponent</code>. Unfortunately by default a UIComponent does not
	 * know its own component type, so subclasses must override this method.
	 * <p>
	 * This method is public for use by NestedLayoutDecorators.
	 */

	public abstract String getComponentType();

	/**
	 * Useful for WidgetBuilders to setup nested Metawidgets (eg. for wrapping them in a
	 * h:column).
	 */

	public void initNestedMetawidget( UIMetawidget nestedMetawidget, Map<String, String> attributes ) {

		// Don't reconfigure...

		nestedMetawidget.setConfig( null );

		// ...instead, copy runtime values

		mPipeline.initNestedPipeline( nestedMetawidget.mPipeline, attributes );

		// Read-only
		//
		// Note: initNestedPipeline takes care of literal values. This is concerned with the value
		// binding

		if ( !WidgetBuilderUtils.isReadOnly( attributes ) ) {
			ValueBinding bindingReadOnly = getValueBinding( "readOnly" );

			if ( bindingReadOnly != null ) {
				nestedMetawidget.setValueBinding( "readOnly", bindingReadOnly );
			}
		}

		// Bundle

		nestedMetawidget.setValueBinding( "bundle", getValueBinding( "bundle" ) );

		// Renderer type

		nestedMetawidget.setRendererType( getRendererType() );

		// Parameters

		nestedMetawidget.copyParameters( this );

		// Note: it is very dangerous to do, say...
		//
		// to.getAttributes().putAll( from.getAttributes() );
		//
		// ...in order to copy all arbitary attributes, because some frameworks (eg. Facelets) use
		// the attributes map as a storage area for special flags (eg.
		// ComponentSupport.MARK_CREATED) that should not get copied down from component to
		// component!
		
		// AJAX
		
		nestedMetawidget.setBuildWidgetsOnAjaxRequest(mBuildWidgetsOnAjaxRequest);
	}

	@Override
	public Object saveState( FacesContext context ) {

		Object[] values = new Object[6];
		values[0] = super.saveState( context );
		values[1] = mExplicitRendererType;
		values[2] = mReadOnly;
		values[3] = mPipeline.getConfig();
		values[4] = mInspectFromParent;
		values[5] = mBuildWidgetsOnAjaxRequest;

		return values;
	}

	@Override
	public void restoreState( FacesContext context, Object state ) {

		Object[] values = (Object[]) state;
		super.restoreState( context, values[0] );

		mExplicitRendererType = (Boolean) values[1];
		mReadOnly = (Boolean) values[2];
		mPipeline.setConfig( values[3] );
		mInspectFromParent = (Boolean) values[4];
		mBuildWidgetsOnAjaxRequest = (Boolean) values[5];
	}

	//
	// Protected methods
	//

	/**
	 * Instantiate the Pipeline used by this Metawidget.
	 * <p>
	 * Subclasses wishing to use their own Pipeline should override this method to instantiate their
	 * version.
	 */

	protected Pipeline newPipeline() {

		return new Pipeline();
	}

	/**
	 * Register the mechanism used to build widgets.
	 * <p>
	 * Subclasses wishing to use their own mechanism should override this method to instantiate
	 * their version.
	 */

	protected void registerBuildWidgetsSupport() {

		FacesContext context = FacesContext.getCurrentInstance();
		ExternalContext externalContext = context.getExternalContext();

		// PreRenderViewEvent support
		//
		// This is dependent on http://java.net/jira/browse/JAVASERVERFACES-1826
		// and https://issues.apache.org/jira/browse/MYFACES-2935. It is decided once, statically,
		// for the duration

		if ( USE_PRERENDER_VIEW_EVENT == null ) {

			// Use context.class. Using context.application.class returns 'JBoss Application Server
			// Weld Integration EE Webtier services' under JBoss AS 6

			Package contextPackage = context.getClass().getPackage();
			String contextImplementationTitle = contextPackage.getImplementationTitle();
			String contextImplementationVersion = contextPackage.getImplementationVersion();

			if ( TRUE.equals( externalContext.getInitParameter( COMPONENT_FAMILY + ".faces.component.DONT_USE_PRERENDER_VIEW_EVENT" ) ) ) {

				if ( isBadMojarra2( contextImplementationTitle, contextImplementationVersion ) && !FacesUtils.isPartialStateSavingDisabled() ) {

					throw MetawidgetException.newException( contextImplementationTitle + " " + contextImplementationVersion + " requires setting 'javax.faces.PARTIAL_STATE_SAVING' to 'false'. Or upgrade Mojarra to a version that includes this fix: http://java.net/jira/browse/JAVASERVERFACES-1826" );

				} else if ( isBadMyFaces2( contextImplementationTitle, contextImplementationVersion ) && !FacesUtils.isPartialStateSavingDisabled() ) {

					throw MetawidgetException.newException( contextImplementationTitle + " " + contextImplementationVersion + " requires setting 'javax.faces.PARTIAL_STATE_SAVING' to 'false'. Or upgrade MyFaces to a version that includes this fix: https://issues.apache.org/jira/browse/MYFACES-2935" );
				}

				// Forcibly disabled

				USE_PRERENDER_VIEW_EVENT = Boolean.FALSE;

			} else if ( FacesUtils.isJsf2() ) {

				if ( isBadMojarra2( contextImplementationTitle, contextImplementationVersion ) ) {

					throw MetawidgetException.newException( contextImplementationTitle + " " + contextImplementationVersion + " requires setting 'org.metawidget.faces.component.DONT_USE_PRERENDER_VIEW_EVENT' to 'true'. Or upgrade Mojarra to a version that includes this fix: http://java.net/jira/browse/JAVASERVERFACES-1826" );

				} else if ( isBadMyFaces2( contextImplementationTitle, contextImplementationVersion ) ) {

					throw MetawidgetException.newException( contextImplementationTitle + " " + contextImplementationVersion + " requires setting 'org.metawidget.faces.component.DONT_USE_PRERENDER_VIEW_EVENT' to 'true'. Or upgrade MyFaces to a version that includes this fix: https://issues.apache.org/jira/browse/MYFACES-2935" );
				}

				if ( context.getViewRoot() == null ) {

					// No ViewRoot? Maybe PARTIAL_STATE_SAVING disabled under MyFaces?

					USE_PRERENDER_VIEW_EVENT = Boolean.FALSE;

				} else {

					// Supported

					USE_PRERENDER_VIEW_EVENT = Boolean.TRUE;
				}

			} else {

				// JSF 1.x

				USE_PRERENDER_VIEW_EVENT = Boolean.FALSE;
			}
		}

		if ( Boolean.TRUE.equals( USE_PRERENDER_VIEW_EVENT ) ) {
			mBuildWidgetsSupport = new PreRenderViewEventSupport( this );
		} else {
			mBuildWidgetsSupport = new EncodeBeginSupport( this );
		}
	}

	/**
	 * Build widgets for the given value binding.
	 * <p>
	 * Subclasses can override this method as a common entry point regardless of whether
	 * PreRenderViewEventSupport (JSF2) or EncodeBeginSupport (JSF1) is being used.
	 */

	protected void buildWidgets()
		throws Exception {

		// Inspect from the value binding...

		ValueBinding valueBinding = getValueBinding( "value" );

		if ( valueBinding != null ) {
			mPipeline.buildWidgets( inspect( valueBinding, mInspectFromParent ) );
			return;
		}

		// ...or from a raw value (for jBPM)...

		Object value = getValue();

		if ( value instanceof String ) {
			mPipeline.buildWidgets( mPipeline.inspectAsDom( null, (String) value ) );
			return;
		}

		// ...or a Class (for 'binding' attribute)...

		if ( value instanceof Class<?> ) {
			mPipeline.buildWidgets( mPipeline.inspectAsDom( null, ( (Class<?>) value ).getName() ) );
			return;
		}

		// ...or a direct Object (for 'binding' attribute)...

		if ( value != null ) {
			mPipeline.buildWidgets( mPipeline.inspectAsDom( value, value.getClass().getName() ) );
			return;
		}

		// ...or run without inspection (using the Metawidget purely for layout)

		mPipeline.buildWidgets( null );
	}

	protected abstract String getDefaultConfiguration();

	/**
	 * Build child widgets.
	 */

	protected void startBuild() {

		LOG.trace( "startBuild" );

		// Metawidget has no valueBinding? Won't be destroying/recreating any components, then.
		//
		// This is an optimisation, but is also important for cases like RichFacesLayout, which
		// use nested Metawidgets (without a value binding) purely for layout. They populate a new
		// Metawidget with previously inspected components, and we don't want them destroyed
		// here and/or unnecessarily re-inspected in endBuild
		//
		// Check getValue() is null too, in case the Metawidget is being used with direct objects
		// or direct classes (through the 'binding' attribute)

		if ( getValueBinding( "value" ) == null && getValue() == null ) {
			return;
		}

		// Remove any components we created previously (this is
		// important for polymorphic controls, which may change from
		// refresh to refresh)

		List<UIComponent> children = getChildren();

		for ( Iterator<UIComponent> i = children.iterator(); i.hasNext(); ) {
			UIComponent componentChild = i.next();
			Map<String, Object> attributes = componentChild.getAttributes();

			// The first time in, children will have no metadata attached. Use this opportunity
			// to tag the initial children so that we never recreate them

			if ( !attributes.containsKey( COMPONENT_ATTRIBUTE_METADATA ) ) {
				attributes.put( COMPONENT_ATTRIBUTE_NOT_RECREATABLE, true );
				continue;
			}

			// Remove recreatable components

			if ( removeRecreatableChildren( componentChild ) ) {
				i.remove();
			}
		}
	}

	/**
	 * @param elementName
	 *            XML node name of the business field. Typically 'entity', 'property' or 'action'.
	 *            Never null
	 */

	protected void layoutWidget( UIComponent component, String elementName, Map<String, String> attributes ) {

		Map<String, Object> componentAttributes = component.getAttributes();
		componentAttributes.put( COMPONENT_ATTRIBUTE_METADATA, attributes );

		// If this component already exists in the list, remove it and re-add it. This
		// enables us to sort existing, manually created components in the correct order
		//
		// Doing the remove here, rather than in SimpleLayout, ensures we always remove and
		// add for cases like moving a Stub from outside a TabPanel to inside it

		getChildren().remove( component );

		// Look up any additional attributes

		Map<String, String> additionalAttributes = mPipeline.getAdditionalAttributes( component );

		if ( additionalAttributes != null ) {
			attributes.putAll( additionalAttributes );
		}

		// BasePipeline will call .layoutWidget
	}

	protected void endBuild() {

		List<UIComponent> children = getChildren();

		// Inspect any remaining components, and sort them to the bottom

		for ( int loop = 0, index = 0, length = children.size(); loop < length; loop++ ) {
			UIComponent component = children.get( index );

			// If this component has already been processed by the inspection (ie. contains
			// metadata), is not rendered, or is a UIParameter, skip it
			//
			// This is also handy for RichFacesLayout, which uses a nested Metawidget purely as a
			// layout tool: it populates a new Metawidget with some previously inspected components.
			// This check makes sure they aren't unnecessarily re-inspected here

			Map<String, Object> miscAttributes = component.getAttributes();

			if ( miscAttributes.containsKey( COMPONENT_ATTRIBUTE_METADATA ) || !component.isRendered() || component instanceof UIParameter ) {
				index++;
				continue;
			}

			// Try and determine some metadata for the component by inspecting its binding. This
			// helps our layout display proper labels, required stars etc. - even for components
			// whose binding is not a descendant of our parent binding

			Map<String, String> childAttributes = CollectionUtils.newHashMap();
			miscAttributes.put( COMPONENT_ATTRIBUTE_METADATA, childAttributes );

			ValueBinding binding = component.getValueBinding( "value" );

			if ( binding != null ) {
				Element inspectionResult = inspect( binding, true );

				if ( inspectionResult != null ) {
					childAttributes.putAll( XmlUtils.getAttributesAsMap( inspectionResult.getFirstChild() ) );
				}
			} else {
				// If no found metadata, default to no section.
				//
				// This is so if a user puts, say, a <t:div/> in the component tree, it doesn't
				// appear inside an existing section

				childAttributes.put( SECTION, "" );
			}

			mPipeline.layoutWidget( component, PROPERTY, childAttributes );
		}

		LOG.trace( "endBuild" );
	}

	//
	// Private methods
	//

	/**
	 * Removes all recreatable children (i.e. not marked COMPONENT_ATTRIBUTE_NOT_RECREATABLE). Does
	 * not remove top-level <code>UIComponent</code>s if any of their
	 * children are COMPONENT_ATTRIBUTE_NOT_RECREATABLE, but <em>does</em> remove as many of their
	 * children as it can. This allows their siblings to still behave dynamically even if some
	 * components are locked (e.g. <code>SelectInputDate</code>).
	 * 
	 * @return true if all children were removed (i.e. none were marked not-recreatable).
	 */

	private boolean removeRecreatableChildren( UIComponent component ) {

		// Do not remove locked or overridden components...

		Map<String, Object> attributes = component.getAttributes();
		if ( attributes.containsKey( COMPONENT_ATTRIBUTE_NOT_RECREATABLE ) ) {

			// ...but always remove their metadata, otherwise
			// they will not be removed/re-added (and therefore re-ordered) upon POSTback

			attributes.remove( COMPONENT_ATTRIBUTE_METADATA );
			return false;
		}

		// Recurse into children. We may have an auto-generated 'not recreatable' (e.g.
		// SelectInputDate) or a manually added 'not recreatable', and we don't want to remove the
		// top-level for it. This includes children that are nested Metawidgets, and children that
		// are LayoutDecorators

		List<UIComponent> children = component.getChildren();
		for ( Iterator<UIComponent> i = children.iterator(); i.hasNext(); ) {

			UIComponent componentChild = i.next();

			if ( removeRecreatableChildren( componentChild ) ) {
				i.remove();
			}
		}

		return children.isEmpty();
	}

	/**
	 * Inspect the value binding.
	 * <p>
	 * A value binding is optional. UIMetawidget can be used purely to lay out manually-specified
	 * components
	 */

	private Element inspect( ValueBinding valueBinding, boolean inspectFromParent ) {

		if ( valueBinding == null ) {
			return null;
		}

		// Inspect the object directly

		FacesContext context = getFacesContext();
		String valueBindingString = valueBinding.getExpressionString();

		if ( !inspectFromParent || !FacesUtils.isExpression( valueBindingString ) ) {
			Object toInspect = valueBinding.getValue( context );

			if ( toInspect != null && !ClassUtils.isPrimitiveWrapper( toInspect.getClass() ) ) {
				return mPipeline.inspectAsDom( toInspect, toInspect.getClass().getName() );
			}
		}

		// In the event the direct object is 'null' or a primitive...

		String binding = FacesUtils.unwrapExpression( valueBindingString );

		// ...and the EL expression is such that we can chop it off to get to the parent...
		//
		// Note: using EL functions in generated ValueExpressions only works in JSF 2.0,
		// see https://javaserverfaces.dev.java.net/issues/show_bug.cgi?id=813. A workaround is
		// to assign the function's return value to a temporary, request-scoped variable using c:set

		if ( binding.indexOf( ' ' ) == -1 && binding.indexOf( StringUtils.SEPARATOR_COLON_CHAR ) == -1 && binding.indexOf( '(' ) == -1 ) {
			int lastIndexOf = binding.lastIndexOf( StringUtils.SEPARATOR_DOT_CHAR );

			if ( lastIndexOf != -1 ) {
				// ...traverse from the parent as there may be useful metadata there (such as 'name'
				// and 'type')

				Application application = context.getApplication();
				ValueBinding bindingParent = application.createValueBinding( FacesUtils.wrapExpression( binding.substring( 0, lastIndexOf ) ) );
				Object toInspect = bindingParent.getValue( context );

				if ( toInspect != null ) {
					return mPipeline.inspectAsDom( toInspect, toInspect.getClass().getName(), binding.substring( lastIndexOf + 1 ) );
				}
			}
		}

		LOG.debug( "No inspectors matched {0} (evaluated to null)", valueBindingString );
		return null;
	}

	/**
	 * Mojarra 2.x requires a fix for http://java.net/jira/browse/JAVASERVERFACES-1826.
	 */

	private boolean isBadMojarra2( String contextImplementationTitle, String contextImplementationVersion ) {

		if ( contextImplementationTitle == null ) {
			return false;
		}

		if ( !contextImplementationTitle.contains( "Mojarra" ) ) {
			return false;
		}

		if ( contextImplementationVersion.endsWith( "2.1.0" ) || contextImplementationVersion.endsWith( "2.1.1" ) || contextImplementationVersion.endsWith( "2.1.2" ) ) {
			return true;
		}

		if ( contextImplementationVersion.endsWith( "2.1.3" ) || contextImplementationVersion.endsWith( "2.1.4" ) || contextImplementationVersion.endsWith( "2.1.5" ) ) {
			return true;
		}

		if ( contextImplementationVersion.endsWith( "2.1.6" ) ) {
			return true;
		}

		return contextImplementationVersion.contains( "2.0." );
	}

	/**
	 * MyFaces 2.x requires a fix for https://issues.apache.org/jira/browse/MYFACES-2935 (and
	 * ideally https://issues.apache.org/jira/browse/MYFACES-3010 too).
	 */

	private boolean isBadMyFaces2( String contextImplementationTitle, String contextImplementationVersion ) {

		if ( contextImplementationTitle == null ) {
			return false;
		}

		if ( !contextImplementationTitle.contains( "MyFaces" ) ) {
			return false;
		}

		return contextImplementationVersion.endsWith( "2.0.0" ) || contextImplementationVersion.endsWith( "2.0.1" ) || contextImplementationVersion.endsWith( "2.0.2" );
	}

	//
	// Inner class
	//

	protected class Pipeline
		extends W3CPipeline<UIComponent, UIComponent, UIMetawidget> {

		//
		// Public methods
		//

		@Override
		protected void configure() {

			boolean wasExplicitRendererType = mExplicitRendererType;
			String rendererType = getRendererType();

			try {
				super.configure();
			} catch ( MetawidgetException e ) {
				if ( !DEFAULT_USER_CONFIG.equals( getConfig() ) || !( e.getCause() instanceof FileNotFoundException ) ) {
					throw e;
				}

				// Log a warning. Still log the Exception message, in case the FileNotFoundException
				// is from inside metawidget.xml, for example 'Unable to locate checkout.jpdl.xml on
				// CLASSPATH'

				if ( !LOGGED_MISSING_CONFIG ) {
					LOGGED_MISSING_CONFIG = true;
					LOG.info( "Could not locate " + DEFAULT_USER_CONFIG + ". This file is optional, but if you HAVE created one then Metawidget isn''t finding it: {0}", e.getMessage() );
				}

				super.configureDefaults();
			}

			// Preserve rendererType if was set explicitly

			if ( wasExplicitRendererType ) {
				setRendererType( rendererType );
			}
		}

		@Override
		protected String getDefaultConfiguration() {

			return UIMetawidget.this.getDefaultConfiguration();
		}

		/**
		 * Overridden to just-in-time evaluate EL binding.
		 */

		@Override
		public boolean isReadOnly() {

			return UIMetawidget.this.isReadOnly();
		}

		@Override
		public void setReadOnly( boolean readOnly ) {

			UIMetawidget.this.setReadOnly( readOnly );
		}

		//
		// Protected methods
		//

		@Override
		protected void startBuild() {

			super.startBuild();
			UIMetawidget.this.startBuild();
		}

		@Override
		protected UIComponent buildWidget( String elementName, Map<String, String> attributes ) {

			UIComponent entityLevelWidget = super.buildWidget( elementName, attributes );

			// If we manage to build an entity-level widget, move our children *inside* it
			//
			// It's pretty rare we'll manage to create an entity-level widget, and even rarer that
			// we'll create one when we ourselves have children, but if we do this allows us to
			// attach, say, f:validator or f:ajax handlers to a dynamically chosen widget

			if ( entityLevelWidget != null && ENTITY.equals( elementName ) ) {

				// Move converters

				if ( entityLevelWidget instanceof ValueHolder ) {

					ValueHolder valueHolder = (ValueHolder) entityLevelWidget;
					valueHolder.setConverter( UIMetawidget.this.getConverter() );

					// Do not UIMetawidget.this.setConverter( null ), else it will get lost for
					// subsequent POSTbacks
				}

				// Move facets

				Map<String, UIComponent> metawidgetFacets = UIMetawidget.this.getFacets();
				Map<String, UIComponent> entityLevelWidgetFacets = entityLevelWidget.getFacets();

				for ( Map.Entry<String, UIComponent> entry : metawidgetFacets.entrySet() ) {

					UIComponent facet = entry.getValue();
					entityLevelWidgetFacets.put( entry.getKey(), facet );

					if ( mBuildWidgetsSupport instanceof EncodeBeginSupport ) {
						( (EncodeBeginSupport) mBuildWidgetsSupport ).reassignFacet( facet );
					}
				}

				metawidgetFacets.clear();

				// Move validators

				if ( entityLevelWidget instanceof EditableValueHolder ) {

					EditableValueHolder entityLevelEditableValueHolder = (EditableValueHolder) entityLevelWidget;

					for ( Validator metawidgetValidator : UIMetawidget.this.getValidators() ) {
						entityLevelEditableValueHolder.addValidator( metawidgetValidator );

						// Do not UIMetawidget.this.removeValidator( metawidgetValidator ), else
						// they will get lost for subsequent POSTbacks
					}
				}

				// It's not clear whether we should move .getChildren() too. Err on the side of
				// caution and don't for now
			}

			return entityLevelWidget;
		}

		@Override
		protected Map<String, String> getAdditionalAttributes( UIComponent widget ) {

			if ( widget instanceof UIStub ) {
				return ( (UIStub) widget ).getStubAttributesAsMap();
			}

			return null;
		}

		@Override
		protected UIMetawidget buildNestedMetawidget( Map<String, String> attributes )
			throws Exception {

			FacesContext context = FacesContext.getCurrentInstance();
			UIMetawidget metawidget = (UIMetawidget) context.getApplication().createComponent( UIMetawidget.this.getComponentType() );

			UIMetawidget.this.initNestedMetawidget( metawidget, attributes );

			return metawidget;
		}

		@Override
		protected void layoutWidget( UIComponent component, String elementName, Map<String, String> attributes ) {

			UIMetawidget.this.layoutWidget( component, elementName, attributes );
			super.layoutWidget( component, elementName, attributes );
		}

		@Override
		protected void endBuild() {

			super.endBuild();
			UIMetawidget.this.endBuild();
		}

		@Override
		protected UIMetawidget getPipelineOwner() {

			return UIMetawidget.this;
		}
	}

	/**
	 * Dynamically modify the component tree using the JSF1 API.
	 * <p>
	 * <h3>Background</h3>
	 * <p>
	 * JSF1 did not have very good support for dynamically modifying the component tree. See
	 * http://osdir.com/ml/java.facelets.user/2008-06/msg00050.html:
	 * <p>
	 * Jacob Hookum: "What's actually needed in [JSF 1.2] is post component tree creation or post
	 * component creation hooks, providing the ability to then modify the component tree"<br/>
	 * Ken Paulsen: "This hasn't been resolved in the 2.0 EG yet"
	 * <p>
	 * We tried various workarounds:
	 * <p>
	 * <ol>
	 * <li>Triggering buildWidgets on getChildCount/getChildren. This does not work because those
	 * methods get called at all sorts of other times</li>
	 * <li>Doing it in super.encodeBegin for a GET, in processUpdates for a POST. This does not work
	 * because the GET still records the bad components</li>
	 * <li>A PhaseListener before PhaseId.RENDER_RESPONSE to trigger buildWidgets. This does not
	 * work because UIViewRoot has no children at that stage in the lifecycle</li>
	 * </ol>
	 * JSF2 introduced <code>SystemEvents</code> to address this exact problem. See
	 * <code>PreRenderViewEventSupport</code> below.
	 * <p>
	 * <h3>Why It's A Problem</h3>
	 * <p>
	 * JSF actually has (sort of) <em>two</em> component trees: the one in the ViewState, and the
	 * components in the original JSP page. The latter is re-merged with the former, then the whole
	 * lot is serialized. This happens after <code>processUpdates</code> but before
	 * <code>encodeBegin</code>, which is a bit of a 'dead zone' for hooking into under JSF1.
	 * <p>
	 * It can cause an Exception if the original JSP contains a manually coded control (such as an
	 * h:inputHidden) that subsequently gets moved into a Metawidget-generated sub-container (such
	 * as a rich:simpleTogglePanel). Now there are two versions of the component: one in the
	 * original JSP and one in a <em>different</em> place in the ViewState.
	 * <p>
	 * This hack removes that duplicate.
	 */

	private static class EncodeBeginSupport
		extends BuildWidgetsSupport {

		//
		// Constructor
		//

		public EncodeBeginSupport( UIMetawidget metawidget ) {

			super( metawidget );
		}

		//
		// Public methods
		//

		/**
		 * If the component is never going to be rendered, then <code>encodeBegin</code> will never
		 * get called. Therefore our 'remove duplicates' code will never get called either.
		 */

		public void isRendered( boolean rendered ) {

			// Note: we explored not doing this until after the processUpdates phase, in case
			// the value of 'rendered' changes, but it didn't seem to make any difference to our
			// unit tests?

			if ( !rendered ) {
				getMetawidget().getChildren().clear();
			}
		}

		/**
		 * Modify the component tree during <code>encodeBegin</code>. This is not the cleanest, but
		 * is the best we can do under JSF 1.x
		 */

		public void encodeBegin()
			throws IOException {

			try {
				// Remove duplicate children
				//
				// Remove the top-level version of each duplicate, not the nested-level version,
				// because the top-level is the 'original' whereas the nested-level is the
				// 'moved' (i.e. at its final destination).

				for ( Iterator<UIComponent> i = getMetawidget().getChildren().iterator(); i.hasNext(); ) {
					UIComponent component = i.next();

					if ( findDuplicateChild( getMetawidget(), component ) != null ) {
						i.remove();
					}
				}

				buildWidgets();
			} catch ( Exception e ) {
				// IOException does not take a Throwable 'cause' argument until Java 6, so
				// as we need to stay 1.5 compatible we output the trace here

				LogUtils.getLog( getClass() ).error( "Unable to encodeBegin", e );

				// At this level, it is more 'proper' to throw an IOException than
				// a MetawidgetException, as that is what the layers above are expecting

				throw new IOException( e.getMessage() );
			}
		}

		/**
		 * Under JSF 1.x, facets will get duplicated just like children do. We can clean them up on
		 * <code>encodeBegin</code>, but in the case of an AJAX POST-back we are not given that
		 * chance. Instead, give them a new unique id.
		 */

		public void reassignFacet( UIComponent facet ) {

			facet.setId( FacesUtils.createUniqueId() );
		}

		//
		// Private methods
		//

		private UIComponent findDuplicateChild( UIComponent componentWithChildren, UIComponent originalComponent ) {

			String id = originalComponent.getId();

			if ( id == null ) {
				return null;
			}

			for ( UIComponent child : componentWithChildren.getChildren() ) {
				if ( child == originalComponent ) {
					continue;
				}

				if ( id.equals( child.getId() ) ) {
					return child;
				}

				UIComponent found = findDuplicateChild( child, originalComponent );

				if ( found != null ) {
					return found;
				}
			}

			return null;
		}
	}

	/**
	 * Dynamically modify the component tree using the JSF2 API.
	 * <p>
	 * JSF2 introduced <code>PreRenderViewEvent</code>, which we can use to avoid
	 * <code>RemoveDuplicatesSupport</code>.
	 */

	private static class PreRenderViewEventSupport
		extends BuildWidgetsSupport
		implements SystemEventListener {

		//
		// Constructor
		//

		public PreRenderViewEventSupport( UIMetawidget metawidget ) {

			super( metawidget );

			FacesContext context = FacesContext.getCurrentInstance();
			UIViewRoot root = context.getViewRoot();

			if ( root == null ) {
				throw MetawidgetException.newException( "UIViewRoot is null. Is faces-config.xml set to version 2.0?" );
			}

			root.subscribeToViewEvent( PreRenderViewEvent.class, this );
		}

		//
		// Public methods
		//

		public boolean isListenerForSource( Object source ) {

			return source instanceof UIViewRoot;
		}

		public void processEvent( SystemEvent event ) {

			// Don't do unnecessary work if none of our child components are to be rendered anyway
			// (work around for https://issues.apache.org/jira/browse/MYFACES-3293)

			if ( !getMetawidget().isRendered() ) {
				return;
			}

			// Don't run if we are not actually part of the View (HtmlWidgetBuilder uses us as a
			// dummy Metawidget)

			if ( getMetawidget().getParent() == null ) {
				return;
			}

			// PartialViewContext (JSF 2-specific)

			if ( !getMetawidget().mBuildWidgetsOnAjaxRequest ) {
				PartialViewContext partialViewContext = FacesContext.getCurrentInstance().getPartialViewContext();

				if ( partialViewContext.isAjaxRequest() ) {

					Collection<String> executeIds = partialViewContext.getExecuteIds();
					if ( !executeIds.contains( getMetawidget().getClientId() ) ) {
						return;
					}
				}
			}

			try {
				buildWidgets();
			} catch ( Exception e ) {
				// At this level, it is more 'proper' to throw an AbortProcessingException than
				// a MetawidgetException, as that is what the layers above are expecting

				throw new AbortProcessingException( e );
			}
		}
	}

	/**
	 * Base implementation shared by <code>EncodeBeginSupport</code> and
	 * <code>PreRenderViewEventSupport</code>.
	 */

	private static class BuildWidgetsSupport {

		//
		// Private members
		//

		private UIMetawidget	mMetawidget;

		//
		// Constructor
		//

		public BuildWidgetsSupport( UIMetawidget metawidget ) {

			mMetawidget = metawidget;
		}

		//
		// Protected methods
		//

		protected UIMetawidget getMetawidget() {

			return mMetawidget;
		}

		protected void buildWidgets()
			throws Exception {

			// Validation error? Do not rebuild, as we will lose the invalid values in the
			// components

			if ( FacesUtils.isValidationFailed() ) {
				return;
			}

			// Build the widgets

			mMetawidget.buildWidgets();
		}
	}
}