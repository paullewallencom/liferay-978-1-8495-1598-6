/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable
 * law or agreed to in writing, software distributed under the License is distributed on an "AS IS"
 * BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 * for the specific language governing permissions and limitations under the License.
 */
package javax.portlet.faces;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.portlet.ActionRequest;
import javax.portlet.ActionResponse;
import javax.portlet.EventRequest;
import javax.portlet.EventResponse;
import javax.portlet.GenericPortlet;
import javax.portlet.PortletConfig;
import javax.portlet.PortletContext;
import javax.portlet.PortletException;
import javax.portlet.PortletMode;
import javax.portlet.PortletRequest;
import javax.portlet.PortletRequestDispatcher;
import javax.portlet.PortletResponse;
import javax.portlet.RenderRequest;
import javax.portlet.RenderResponse;
import javax.portlet.ResourceRequest;
import javax.portlet.ResourceResponse;
import javax.portlet.WindowState;

/**
 * The <code>GenericFacesPortlet</code> is provided to simplify development of a portlet that in
 * whole or part relies on the Faces bridge to process requests. If all requests are to be handled
 * by the bridge, <code>GenericFacesPortlet</code> is a turnkey implementation. Developers do not
 * need to subclass it. However, if there are some situations where the portlet doesn't require
 * bridge services then <code>GenericFacesPortlet</code> can be subclassed and overriden.
 * <p>
 * Since <code>GenericFacesPortlet</code> subclasses <code>GenericPortlet</code> care is taken
 * to all subclasses to override naturally. For example, though <code>doDispatch()</code> is
 * overriden, requests are only dispatched to the bridge from here if the <code>PortletMode</code>
 * isn't <code>VIEW</code>, <code>EDIT</code>, or <code>HELP</code>.
 * <p>
 * The <code>GenericFacesPortlet</code> recognizes the following portlet initialization
 * parameters:
 * <ul>
 * <li><code>javax.portlet.faces.defaultViewId.[<i>mode</i>]</code>: specifies on a per mode
 * basis the default viewId the Bridge executes when not already encoded in the incoming request. A
 * value must be defined for each <code>PortletMode</code> the <code>Bridge</code> is expected
 * to process. </li>
 *  <li><code>javax.portlet.faces.excludedRequestAttributes</code>: specifies on a per portlet
 * basis the set of request attributes the bridge is to exclude from its request scope.  The
 * value of this parameter is a comma delimited list of either fully qualified attribute names or
 * a partial attribute name of the form <i>packageName.*</i>.  In this later case all attributes
 * exactly prefixed by <i>packageName</i> are excluded, non recursive.</li>
 *  <li><code>javax.portlet.faces.preserveActionParams</code>: specifies on a per portlet
 * basis whether the bridge should preserve parameters received in an action request
 * and restore them for use during subsequent renders.</li>
 *  <li><code>javax.portlet.faces.defaultContentType</code>: specifies on a per mode
 * basis the content type the bridge should set for all render requests it processes. </li>
 *  <li><code>javax.portlet.faces.defaultCharacterSetEncoding</code>: specifies on a per mode
 * basis the default character set encoding the bridge should set for all render requests it
 * processes</li>
 * </ul>
 * The <code>GenericFacesPortlet</code> recognizes the following application
 * (<code>PortletContext</code>) initialization parameters:
 * <ul>
 * <li><code>javax.portlet.faces.BridgeImplClass</code>: specifies the <code>Bridge</code>implementation
 * class used by this portlet. Typically this initialization parameter isn't set as the 
 * <code>GenericFacesPortlet</code> defaults to finding the class name from the bridge
 * configuration.  However if more then one bridge is configured in the environment such 
 * per application configuration is necessary to force a specific bridge to be used.
 * </li>
 * </ul>
 */
public class GenericFacesPortlet extends GenericPortlet
{
  /** Application (PortletContext) init parameter that names the bridge class used
   * by this application.  Typically not used unless more then 1 bridge is configured
   * in an environment as its more usual to rely on the self detection.
   */
  public static final String BRIDGE_CLASS = Bridge.BRIDGE_PACKAGE_PREFIX + "BridgeClassName";
  
  
  /** Portlet init parameter that defines the default ViewId that should be used
   * when the request doesn't otherwise convery the target.  There must be one 
   * initialization parameter for each supported mode.  Each parameter is named
   * DEFAULT_VIEWID.<i>mode</i>, where <i>mode</i> is the name of the corresponding
   * <code>PortletMode</code>
   */
  public static final String DEFAULT_VIEWID = Bridge.BRIDGE_PACKAGE_PREFIX + "defaultViewId";


  /** Portlet init parameter that defines the render response ContentType the bridge 
   * sets prior to rendering.  If not set the bridge uses the request's preferred
   * content type.
   */
  public static final String DEFAULT_CONTENT_TYPE = 
    Bridge.BRIDGE_PACKAGE_PREFIX + "defaultContentType";

  /** Portlet init parameter that defines the render response CharacterSetEncoding the bridge 
   * sets prior to rendering.  Typcially only set when the jsp outputs an encoding other
   * then the portlet container's and the portlet container supports response encoding
   * transformation.
   */
  public static final String DEFAULT_CHARACTERSET_ENCODING = 
    Bridge.BRIDGE_PACKAGE_PREFIX + "defaultCharacterSetEncoding";
  
    /** Portlet init parameter containing the setting for whether the <code>GenericFacesPortlet</code>
     * overrides event processing by dispatching all events to the bridge or delegates
     * all event processing to the <code>GenericPortlet</code>.  Default is <code>true</code>.
     */
  public static final String BRIDGE_AUTO_DISPATCH_EVENTS = Bridge.BRIDGE_PACKAGE_PREFIX + "autoDispatchEvents";

  /** Location of the services descriptor file in a brige installation that defines 
   * the class name of the bridge implementation.
   */
  public static final String BRIDGE_SERVICE_CLASSPATH = 
    "META-INF/services/javax.portlet.faces.Bridge";
  
  private static final String LOGGING_ENABLED = "org.apache.myfaces.portlet.faces.loggingEnabled";

  private Class<? extends Bridge> mFacesBridgeClass = null;
  private Bridge mFacesBridge = null;
  private HashMap<String, String> mDefaultViewIdMap = null;
  private Object mLock = new Object();  // used to synchronize on when initializing the bridge.

  /**
   * Initialize generic faces portlet from portlet.xml
   */
  @SuppressWarnings("unchecked")
  @Override
  public void init(PortletConfig portletConfig) throws PortletException
  {
    super.init(portletConfig);

    // Make sure the bridge impl class is defined -- if not then search for it
    // using same search rules as Faces
    String bridgeClassName = getBridgeClassName();

    if (bridgeClassName != null)
    {
      try
      {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        mFacesBridgeClass = (Class<? extends Bridge>) loader.loadClass(bridgeClassName);
      } catch (ClassNotFoundException cnfe)
      {
        throw new PortletException("Unable to load configured bridge class: " + bridgeClassName);
      }
    }
    else
    {
      throw new PortletException("Can't locate configuration parameter defining the bridge class to use for this portlet:" + getPortletName());
    }

    // Get the other bridge configuration parameters and set as context attributes
    List<String> excludedAttrs = getExcludedRequestAttributes();
    if (excludedAttrs != null)
    {
      getPortletContext().setAttribute(Bridge.BRIDGE_PACKAGE_PREFIX + getPortletName() + "." + 
                                       Bridge.EXCLUDED_REQUEST_ATTRIBUTES, excludedAttrs);
    }

    Boolean preserveActionParams = new Boolean(isPreserveActionParameters());
    getPortletContext().setAttribute(Bridge.BRIDGE_PACKAGE_PREFIX + getPortletName() + "." + 
                                     Bridge.PRESERVE_ACTION_PARAMS, preserveActionParams);
    
    String defaultRenderKitId = getDefaultRenderKitId();
    if (defaultRenderKitId != null)
    {
      getPortletContext().setAttribute(Bridge.BRIDGE_PACKAGE_PREFIX + getPortletName() + "." + 
                                     Bridge.DEFAULT_RENDERKIT_ID, defaultRenderKitId);
    }

    Map defaultViewIdMap = getDefaultViewIdMap();
    getPortletContext().setAttribute(Bridge.BRIDGE_PACKAGE_PREFIX + getPortletName() + "." + 
                                     Bridge.DEFAULT_VIEWID_MAP, defaultViewIdMap);
    
    BridgeEventHandler eventHandler = getBridgeEventHandler();
    if (eventHandler != null)
    {
      getPortletContext().setAttribute(Bridge.BRIDGE_PACKAGE_PREFIX + getPortletName() + "." + 
                                       Bridge.BRIDGE_EVENT_HANDLER, eventHandler);
    }
    
    BridgePublicRenderParameterHandler prpHandler = getBridgePublicRenderParameterHandler();
    if (prpHandler != null)
    {
      getPortletContext().setAttribute(Bridge.BRIDGE_PACKAGE_PREFIX + getPortletName() + "." + 
                                       Bridge.BRIDGE_PUBLIC_RENDER_PARAMETER_HANDLER, prpHandler);
    }
    
    // See if this portlet has enabled informational logging messages
    String s = getPortletConfig().getInitParameter(LOGGING_ENABLED);
    Boolean b = Boolean.valueOf(s);
    getPortletContext().setAttribute(Bridge.BRIDGE_PACKAGE_PREFIX + getPortletName() + "." + 
                                       LOGGING_ENABLED, b);

    // Initialize the bridge as the double lock mechanism used for lazy instantiation doesn't (always) work in Java even if 
    // declared a volitle -- and the bridge is likely to be used anyway -- so why worry about it
    initBridge();
  }

  /**
   * Release resources, specifically it destroys the bridge.
   */
  @Override
  public void destroy()
  {
    if (mFacesBridge != null)
    {
      mFacesBridge.destroy();
      mFacesBridge = null;
      mFacesBridgeClass = null;
    }
    mDefaultViewIdMap = null;
    
    super.destroy();
  }

  /**
   * If mode is VIEW, EDIT, or HELP -- defer to the doView, doEdit, doHelp so subclasses can
   * override. Otherwise handle mode here if there is a defaultViewId mapping for it.
   */
  @Override
  public void doDispatch(RenderRequest request, RenderResponse response) throws PortletException, 
                                                                                IOException
  {
    // Defer to helper methods for standard modes so subclasses can override
    PortletMode mode = request.getPortletMode();
    if (mode.equals(PortletMode.EDIT) || mode.equals(PortletMode.HELP) || mode.equals(PortletMode.VIEW))
    {
      super.doDispatch(request, response);
    } else
    {
      // Bridge didn't process this one -- so forge ahead
      if (!doRenderDispatchInternal(request, response))
      {
        super.doDispatch(request, response);
      }
    }
  }

  @Override
  protected void doEdit(RenderRequest request, RenderResponse response) throws PortletException, 
                                                                               java.io.IOException
  {
    doRenderDispatchInternal(request, response);
  }

  @Override
  protected void doHelp(RenderRequest request, RenderResponse response) throws PortletException, 
                                                                               java.io.IOException
  {
    doRenderDispatchInternal(request, response);
  }

  @Override
  protected void doView(RenderRequest request, RenderResponse response) throws PortletException, 
                                                                               java.io.IOException
  {
    doRenderDispatchInternal(request, response);
  }

  @Override
  public void processAction(ActionRequest request, 
                            ActionResponse response) throws PortletException, IOException
  {
    doActionDispatchInternal(request, response);
  }

  /**
   * Handles resource requests and dispatches to the Bridge
   */
  @Override
  public void serveResource(ResourceRequest request, 
                            ResourceResponse response) throws PortletException, IOException
  {
    doBridgeDispatch(request, response);

  }

  /**
   * Returns an instance of a BridgeEventHandler used to process portlet events
   * in a JSF environment.
   * This default implementation looks for a portlet initParameter that
   * names the class used to instantiate the handler.
   * @return an instance of BridgeEventHandler or null if there is none.
   */
  public BridgeEventHandler getBridgeEventHandler()
  {
    String eventHandlerClass = 
      getPortletConfig().getInitParameter(Bridge.BRIDGE_PACKAGE_PREFIX + Bridge.BRIDGE_EVENT_HANDLER);
    if (eventHandlerClass != null)
    {
      try
      {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        Class<? extends BridgeEventHandler> c = 
          (Class<? extends BridgeEventHandler>) loader.loadClass(eventHandlerClass);
        return c.newInstance();
      } catch (ClassNotFoundException cnfe)
      {
        // Do nothing and fall through to null check
        // TODO: log something
      } catch (InstantiationException ie)
      {
        // Do nothing and fall through to null check
        // TODO: log something
      } catch (Exception e)
      {
        // Do nothing and fall through to null check
        // TODO: log something
      }
    }

    return null;
  }
  
  /**
   * Returns an instance of a BridgePublicRenderParameterHandler used to post
   * process public render parameter changes that the bridge
   * has pushed into mapped models.
   * This default implementation looks for a portlet initParameter that
   * names the class used to instantiate the handler.
   * @return an instance of BridgeRenderParameterHandler or null if there is none.
   */
  public BridgePublicRenderParameterHandler getBridgePublicRenderParameterHandler()
  {
    String prpHandlerClass = 
      getPortletConfig().getInitParameter(Bridge.BRIDGE_PACKAGE_PREFIX + Bridge.BRIDGE_PUBLIC_RENDER_PARAMETER_HANDLER);
    if (prpHandlerClass != null)
    {
      try
      {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        Class<? extends BridgePublicRenderParameterHandler> c = 
          (Class<? extends BridgePublicRenderParameterHandler>) loader.loadClass(prpHandlerClass);
        return c.newInstance();
      } catch (ClassNotFoundException cnfe)
      {
        // Do nothing and fall through to null check
        // TODO: log something
      } catch (InstantiationException ie)
      {
        // Do nothing and fall through to null check
        // TODO: log something
      } catch (Exception e)
      {
        // Do nothing and fall through to null check
        // TODO: log something
      }
    }

    return null;
  }



  /**
   * Returns the set of RequestAttribute names that the portlet wants the bridge to
   * exclude from its managed request scope.  This default implementation picks up
   * this list from the comma delimited init_param javax.portlet.faces.excludedRequestAttributes.
   * 
   * @return a List containing the names of the attributes to be excluded. null if it can't be
   *         determined.
   */
  public List<String> getExcludedRequestAttributes()
  {
    String excludedAttrs = 
      getPortletConfig().getInitParameter(Bridge.BRIDGE_PACKAGE_PREFIX + Bridge.EXCLUDED_REQUEST_ATTRIBUTES);
    if (excludedAttrs == null)
    {
      return null;
    }

    String[] attrArray = excludedAttrs.split(",");
    // process comma delimited String into a List
    ArrayList<String> list = new ArrayList(attrArray.length);
    for (int i = 0; i < attrArray.length; i++)
    {
      list.add(attrArray[i].trim());
    }
    return list;
  }

  /**
   * Returns a boolean indicating whether or not the bridge should preserve all the
   * action parameters in the subsequent renders that occur in the same scope.  This
   * default implementation reads the values from the portlet init_param
   * javax.portlet.faces.preserveActionParams.  If not present, false is returned.
   * 
   * @return a boolean indicating whether or not the bridge should preserve all the
   * action parameters in the subsequent renders that occur in the same scope.
   */
  public boolean isPreserveActionParameters()
  {
    String preserveActionParams = 
      getPortletConfig().getInitParameter(Bridge.BRIDGE_PACKAGE_PREFIX + 
                                          Bridge.PRESERVE_ACTION_PARAMS);
    if (preserveActionParams == null)
    {
      return false;
    } else
    {
      return Boolean.parseBoolean(preserveActionParams);
    }
  }
  
  /**
   * Returns a String defining the default render kit id the bridge should ensure for this portlet.
   * If non-null, this value is used to override any default render kit id set on an app wide basis 
   * in the faces-config.xml. This
   * default implementation reads the values from the portlet init_param
   * javax.portlet.faces.defaultRenderKitId.  If not present, null is returned.
   * 
   * @return a boolean indicating whether or not the bridge should preserve all the
   * action parameters in the subsequent renders that occur in the same scope.
   */
  public String getDefaultRenderKitId()
  {
    return 
      getPortletConfig().getInitParameter(Bridge.BRIDGE_PACKAGE_PREFIX + 
                                          Bridge.DEFAULT_RENDERKIT_ID);
  }   

  /**
   * Returns the className of the bridge implementation this portlet uses. Subclasses override to
   * alter the default behavior. Default implementation first checks for a portlet context init
   * parameter: javax.portlet.faces.BridgeImplClass. If it doesn't exist then it looks for the
   * resource file "META-INF/services/javax.portlet.faces.Bridge" using the current threads
   * classloader and extracts the classname from the first line in that file.
   * 
   * @return the class name of the Bridge class the GenericFacesPortlet uses. null if it can't be
   *         determined.
   */
  public String getBridgeClassName()
  {
    String bridgeClassName = getPortletConfig().getPortletContext().getInitParameter(BRIDGE_CLASS);

    if (bridgeClassName == null)
    {
      bridgeClassName = 
          getFromServicesPath(getPortletConfig().getPortletContext(), BRIDGE_SERVICE_CLASSPATH);
    }
    return bridgeClassName;
  }

  /**
   * @deprecated -- no longer used or called by the <code>GenericFacesPortlet</code>
   * but retained in case a subclass has called it. 
   * 
   * 
   * @return request.getResponseContentType().
   */
  @Deprecated
  public String getResponseContentType(PortletRequest request)
  {
    return request.getResponseContentType();
  }
  
  private boolean isInRequestedContentTypes(PortletRequest request, String contentTypeToCheck)
  {
    Enumeration e = request.getResponseContentTypes();
    while (e.hasMoreElements()) 
    {
      if (contentTypeToCheck.equalsIgnoreCase((String) e.nextElement()))
      {
        return true;
      }
    }
    return false;
  }

  /**
   * @deprecated -- no longer used or called by the <code>GenericFacesPortlet</code>
   * but retained in case a subclass has called it. 
   * 
   * 
   * @return null.
   */
  @Deprecated
  public String getResponseCharacterSetEncoding(PortletRequest request)
  {
    return null;
  }


  /**
   * Returns the defaultViewIdMap the bridge should use when its unable to resolve to a specific
   * target in the incoming request. There is one entry per support <code>PortletMode
   * </code>.  The entry key is the name of the mode.  The entry value is the default viewId
   * for that mode.
   * 
   * @return the defaultViewIdMap
   */
  public Map getDefaultViewIdMap()
  {
    if (mDefaultViewIdMap == null)
    {
      mDefaultViewIdMap = new HashMap<String, String>();
      // loop through all portlet initialization parameters looking for those in the
      // correct form
      PortletConfig config = getPortletConfig();

      Enumeration<String> e = config.getInitParameterNames();
      int len = DEFAULT_VIEWID.length();
      while (e.hasMoreElements())
      {
        String s = e.nextElement();
        if (s.startsWith(DEFAULT_VIEWID) && s.length() > DEFAULT_VIEWID.length())
        {
          String viewId = config.getInitParameter(s);
          
          // Don't add if there isn't a view
          if (viewId == null || viewId.length() == 0) continue;
          
          // extract the mode
          s = s.substring(len + 1);
          mDefaultViewIdMap.put(s, viewId);
        }
      }
    }

    return mDefaultViewIdMap;
  }
  
  /**
   * Returns the value of the portlet initialization parameter
   * <code>javax.portlet.faces.autoDispatchEvents</code> if non-null or
   * <code>true</code>, otherwise.
   * 
   * @return boolean indicating whether to auto-dispatch all events to the bridge 
   * or not.
   */
  public boolean isAutoDispatchEvents()
  {
    String configParam = 
      getPortletConfig().getInitParameter(BRIDGE_AUTO_DISPATCH_EVENTS);

    if (configParam != null)
    {
      return Boolean.parseBoolean(configParam);
    }
    else
    {
      return true;
    }
  }
  
  /**
   * Returns an initialized bridge instance adequately prepared so the caller can
   * call doFacesRequest directly without further initialization.
   * 
   * @return instance of the bridge.
   * @throws PortletException exception acquiring or initializting the bridge.
   */
  public Bridge getFacesBridge(PortletRequest request, 
                               PortletResponse response) throws PortletException
  {
    initBridgeRequest(request, response);
    return mFacesBridge;
  }  
  
  public void processEvent(EventRequest request, EventResponse response)
    throws PortletException, java.io.IOException
  {
    if (isAutoDispatchEvents())
    {
      try
      {
        getFacesBridge(request, response).doFacesRequest(request, response);
      } catch (BridgeException e)
      {
        throw new PortletException("doBridgeDispatch failed:  error from Bridge in executing the request", 
                                   e);
      }
    }
    else
    {
      super.processEvent(request, response);
    }
  }

  private boolean isNonFacesRequest(PortletRequest request, PortletResponse response)
  {
    // Non Faces request is identified by either the presence of the _jsfBridgeNonFacesView
    // parameter or the request being for a portlet mode which doesn't have a default
    // Faces view configured for it.
    if (request.getParameter(Bridge.NONFACES_TARGET_PATH_PARAMETER) != null)
    {
      return true;
    }

    String modeDefaultViewId = mDefaultViewIdMap.get(request.getPortletMode().toString());
    return modeDefaultViewId == null;
  }

  private void doActionDispatchInternal(ActionRequest request, 
                                        ActionResponse response) throws PortletException, 
                                                                        IOException
  {
    // First determine whether this is a Faces or nonFaces request
    if (isNonFacesRequest(request, response))
    {
      throw new PortletException("GenericFacesPortlet:  Action request is not for a Faces target.  Such nonFaces requests must be handled by a subclass.");
    } else
    {
      doBridgeDispatch(request, response);
    }
  }

  private boolean doRenderDispatchInternal(RenderRequest request, 
                                           RenderResponse response) throws PortletException, 
                                                                           IOException
  {
    // First determine whether this is a Faces or nonFaces request
    if (isNonFacesRequest(request, response))
    {
      return doNonFacesDispatch(request, response);
    } else
    {
      WindowState state = request.getWindowState();
      if (!state.equals(WindowState.MINIMIZED))
      {
        doBridgeDispatch(request, response);
      }
      return true;
    }
  }

  private boolean doNonFacesDispatch(RenderRequest request, 
                                     RenderResponse response) throws PortletException
  {
    // Can only dispatch if the path is encoded in the request parameter
    String targetPath = request.getParameter(Bridge.NONFACES_TARGET_PATH_PARAMETER);
    if (targetPath == null)
    {
      // Didn't handle this request
      return false;
    }

    try
    {
      PortletRequestDispatcher dispatcher = 
        this.getPortletContext().getRequestDispatcher(targetPath);
      dispatcher.forward(request, response);
      return true;
    } catch (Exception e)
    {
      throw new PortletException("Unable to dispatch to: " + targetPath, e);
    }
  }

  private void doBridgeDispatch(RenderRequest request, 
                                RenderResponse response) throws PortletException
  {
    try
    {
      getFacesBridge(request, response).doFacesRequest(request, response);
    } catch (BridgeException e)
    {
      throw new PortletException("doBridgeDispatch failed:  error from Bridge in executing the request", 
                                 e);
    }

  }

  private void doBridgeDispatch(ActionRequest request, 
                                ActionResponse response) throws PortletException
  {
    try
    {
      getFacesBridge(request, response).doFacesRequest(request, response);
    } catch (BridgeException e)
    {
      throw new PortletException("doBridgeDispatch failed:  error from Bridge in executing the request", 
                                 e);
    }

  }

  private void doBridgeDispatch(ResourceRequest request, 
                                ResourceResponse response) throws PortletException
  {
    try
    {
      getFacesBridge(request, response).doFacesRequest(request, response);
    } catch (BridgeException e)
    {
      throw new PortletException("doBridgeDispatch failed:  error from Bridge in executing the request", 
                                 e);
    }

  }

  private void initBridgeRequest(PortletRequest request, 
                                 PortletResponse response) throws PortletException
  {
    // Now do any per request initialization
    // In this case look to see if the request is encoded (usually 
    // from a NonFaces view response) with the specific Faces
    // view to execute.
    String view = request.getParameter(Bridge.FACES_VIEW_ID_PARAMETER);
    if (view != null)
    {
      request.setAttribute(Bridge.VIEW_ID, view);
    } else
    {
      view = request.getParameter(Bridge.FACES_VIEW_PATH_PARAMETER);
      if (view != null)
      {
        request.setAttribute(Bridge.VIEW_PATH, view);
      }
    }
  }

  private void initBridge() throws PortletException
  {
    // Ensure te Bridge has been constrcuted and initialized
    if (mFacesBridge == null)
    {
      try
      {
        // ensure we only ever create/init one bridge per portlet
        if (mFacesBridge == null)
        {
          mFacesBridge = mFacesBridgeClass.newInstance();
          mFacesBridge.init(getPortletConfig());
        }
      }
      catch (Exception e)
      {
        throw new PortletException("doBridgeDisptach:  error instantiating the bridge class", e);
      }
    }
  }

  private String getFromServicesPath(PortletContext context, String resourceName)
  {
    // Check for a services definition
    String result = null;
    BufferedReader reader = null;
    InputStream stream = null;
    try
    {
      ClassLoader cl = Thread.currentThread().getContextClassLoader();
      if (cl == null)
      {
        return null;
      }

      stream = cl.getResourceAsStream(resourceName);
      if (stream != null)
      {
        // Deal with systems whose native encoding is possibly
        // different from the way that the services entry was created
        try
        {
          reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
        } catch (UnsupportedEncodingException e)
        {
          reader = new BufferedReader(new InputStreamReader(stream));
        }
        result = reader.readLine();
        if (result != null)
        {
          result = result.trim();
        }
        reader.close();
        reader = null;
        stream = null;
      }
    } catch (IOException e)
    {
    } catch (SecurityException e)
    {
    } finally
    {
      if (reader != null)
      {
        try
        {
          reader.close();
          stream = null;
        } catch (Throwable t)
        {
          ;
        }
        reader = null;
      }
      if (stream != null)
      {
        try
        {
          stream.close();
        } catch (Throwable t)
        {
          ;
        }
        stream = null;
      }
    }
    return result;
  }

}
