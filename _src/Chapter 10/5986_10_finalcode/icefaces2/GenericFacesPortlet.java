/**
 * Copyright (c) 2010-2011 portletfaces.org All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.portletfaces.bridge;

import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import javax.portlet.ActionRequest;
import javax.portlet.ActionResponse;
import javax.portlet.EventRequest;
import javax.portlet.EventResponse;
import javax.portlet.GenericPortlet;
import javax.portlet.PortletConfig;
import javax.portlet.PortletContext;
import javax.portlet.PortletException;
import javax.portlet.PortletRequest;
import javax.portlet.PortletResponse;
import javax.portlet.RenderRequest;
import javax.portlet.RenderResponse;
import javax.portlet.ResourceRequest;
import javax.portlet.ResourceResponse;


/**
 * @author  Neil Griffin
 */
public class GenericFacesPortlet extends GenericPortlet {

	// Public Constants
	public static final String BRIDGE_CLASS = "javax.portlet.faces.BridgeImplClass";
	public static final String BRIDGE_SERVICE_CLASSPATH = "META-INF/services/javax.portlet.faces.Bridge";
	public static final String DEFAULT_VIEWID = "javax.portlet.faces.defaultViewId";

	// Private Data Members
	private Bridge bridge;
	private BridgeEventHandler bridgeEventHandler;
	private BridgePublicRenderParameterHandler bridgePublicRenderParameterHandler;
	private Map<String, String> defaultViewIdMap;
	private PortletConfig portletConfig;

	@Override
	public void destroy() {
		super.destroy();
	}

	@Override
	public void init(PortletConfig portletConfig) throws PortletException {

		// Delegate preliminary initialization to the parent class.
		this.portletConfig = portletConfig;
		super.init(portletConfig);

		// Initialize the bridge implementation instance.
		getFacesBridge().init(portletConfig);

		// According to Section 3.2 of the JSR 329 Spec, the default views specified as <init-param /> elements in
		// portlet.xml must be saved as a Map in a portlet context attribute.
		String portletName = portletConfig.getPortletName();
		PortletContext portletContext = portletConfig.getPortletContext();
		String attrNameDefaultViewIdMap = Bridge.BRIDGE_PACKAGE_PREFIX + portletName + "." + Bridge.DEFAULT_VIEWID_MAP;
		portletContext.setAttribute(attrNameDefaultViewIdMap, getDefaultViewIdMap());

		// If a javax.portlet.faces.bridgeEventHandler is registered as an init-param in portlet.xml, then obtain an
		// instance of the handler and save it as a portlet context attribute as required by Section 3.2 of the JSR 329
		// Spec.
		BridgeEventHandler bridgeEventHandlerInstance = getBridgeEventHandler();

		if (bridgeEventHandlerInstance != null) {

			// Attribute name format: javax.portlet.faces.{portlet-name}.bridgeEventHandler
			String bridgeEventHandlerAttributeName = Bridge.BRIDGE_PACKAGE_PREFIX + portletConfig.getPortletName() +
				"." + Bridge.BRIDGE_EVENT_HANDLER;
			portletContext.setAttribute(bridgeEventHandlerAttributeName, bridgeEventHandlerInstance);
		}

		// If a javax.portlet.faces.bridgePublicRenderParameterHandler is registered as an init-param in portlet.xml,
		// then obtain an instance of the handler and save it as a portlet context attribute as required by Section 3.2
		// of the JSR 329 Spec.
		BridgePublicRenderParameterHandler bridgePublicRenderParameterHandlerInstance =
			getBridgePublicRenderParameterHandler();

		if (bridgePublicRenderParameterHandlerInstance != null) {

			// Attribute name format: javax.portlet.faces.{portlet-name}.bridgePublicRenderParameterHandler
			String bridgeEventHandlerAttributeName = Bridge.BRIDGE_PACKAGE_PREFIX + portletConfig.getPortletName() +
				"." + Bridge.BRIDGE_PUBLIC_RENDER_PARAMETER_HANDLER;
			portletContext.setAttribute(bridgeEventHandlerAttributeName, bridgePublicRenderParameterHandlerInstance);
		}
	}

	@Override
	public void processAction(ActionRequest actionRequest, ActionResponse actionResponse) throws PortletException,
		IOException {
		Bridge facesBridge = getFacesBridge(actionRequest, actionResponse);
		facesBridge.doFacesRequest(actionRequest, actionResponse);
	}

	@Override
	public void processEvent(EventRequest eventRequest, EventResponse eventResponse) throws PortletException,
		IOException {

		// Call the superclass method in GenericPortlet in order to let subclasses of GenericFacesPortlet take
		// advantage of the @ProcessEvent Portlet API annotation if desired.
		super.processEvent(eventRequest, eventResponse);

		// Invoke the bridge implementations handling of the EVENT_PHASE.
		Bridge facesBridge = getFacesBridge(eventRequest, eventResponse);
		facesBridge.doFacesRequest(eventRequest, eventResponse);
	}

	@Override
	public void serveResource(ResourceRequest resourceRequest, ResourceResponse resourceResponse)
		throws PortletException, IOException {
		Bridge facesBridge = getFacesBridge(resourceRequest, resourceResponse);
		facesBridge.doFacesRequest(resourceRequest, resourceResponse);
	}

	@Override
	protected void doEdit(RenderRequest renderRequest, RenderResponse renderResponse) throws PortletException,
		IOException {
		Bridge facesBridge = getFacesBridge(renderRequest, renderResponse);
		facesBridge.doFacesRequest(renderRequest, renderResponse);
	}

	@Override
	protected void doHeaders(RenderRequest renderRequest, RenderResponse renderResponse) {

		try {

			// Streaming portals like WebSphere (as opposed to buffered portals like Liferay) will set the
			// javax.portlet.render_part request attribute to a value of "RENDER_HEADERS" which will cause
			// javax.portlet.GenericPortlet (the superclass of this class) to call this doHeaders(RenderRequest,
			// RenderResponse) method, but will not in turn call GenericPortlet.doDispatch(RenderRequest,
			// RenderResponse). That also means that that the doView(RenderRequest, RenderResponse) will not be called
			// in this class. So if the attribute is set, we call the Bridge.doFacesRequest(RenderRequest,
			// RenderResponse) method here, so that the Faces lifecycle can be run, and resources added to
			// h:head can be retrieved. Note that it is the responsibility of the bridge to check for this
			// attribute as well, because at this point the bridge should not render any JSF views to the response.
			Object renderPartAttribute = renderRequest.getAttribute(RenderRequest.RENDER_PART);

			if ((renderPartAttribute != null) && renderPartAttribute.equals(RenderRequest.RENDER_HEADERS)) {
				Bridge facesBridge = getFacesBridge(renderRequest, renderResponse);
				facesBridge.doFacesRequest(renderRequest, renderResponse);
			}
		}
		catch (PortletException e) {

			// Unfortunately the signature for GenericPortlet.doHeaders(RenderRequest, RenderResponse) does not throw
			// an exception, so we have no choice but to simply report any exceptions by printing the stacktrace.
			e.printStackTrace();
		}
	}

	@Override
	protected void doHelp(RenderRequest renderRequest, RenderResponse renderResponse) throws PortletException,
		IOException {
		Bridge facesBridge = getFacesBridge(renderRequest, renderResponse);
		facesBridge.doFacesRequest(renderRequest, renderResponse);
	}

	@Override
	protected void doView(RenderRequest renderRequest, RenderResponse renderResponse) throws PortletException,
		IOException {
		Bridge facesBridge = getFacesBridge(renderRequest, renderResponse);
		facesBridge.doFacesRequest(renderRequest, renderResponse);
	}

	public String getBridgeClassName() {

		PortletContext portletContext = portletConfig.getPortletContext();
		BridgeFactory bridgeFactory = (BridgeFactory) BridgeFactoryFinder.getInstance().getFactory(
				BridgeFactoryFinder.BRIDGE_FACTORY);

		return bridgeFactory.getBridgeClassName(portletContext);
	}

	protected BridgeEventHandler getBridgeEventHandler() throws PortletException {

		if (bridgeEventHandler == null) {
			String initParamName = Bridge.BRIDGE_PACKAGE_PREFIX + Bridge.BRIDGE_EVENT_HANDLER;
			String className = portletConfig.getInitParameter(initParamName);

			if (className != null) {
				ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

				try {
					Class<?> clazz = classLoader.loadClass(className);
					bridgeEventHandler = (BridgeEventHandler) clazz.newInstance();
				}
				catch (Exception e) {
					throw new PortletException(e);
				}
			}
		}

		return bridgeEventHandler;
	}

	protected BridgePublicRenderParameterHandler getBridgePublicRenderParameterHandler() throws PortletException {

		if (bridgePublicRenderParameterHandler == null) {
			String initParamName = Bridge.BRIDGE_PACKAGE_PREFIX + Bridge.BRIDGE_PUBLIC_RENDER_PARAMETER_HANDLER;
			String className = portletConfig.getInitParameter(initParamName);

			if (className != null) {
				ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

				try {
					Class<?> clazz = classLoader.loadClass(className);
					bridgePublicRenderParameterHandler = (BridgePublicRenderParameterHandler) clazz.newInstance();
				}
				catch (Exception e) {
					throw new PortletException(e);
				}
			}
		}

		return bridgePublicRenderParameterHandler;
	}

	public Map<String, String> getDefaultViewIdMap() {

		if (defaultViewIdMap == null) {
			defaultViewIdMap = new HashMap<String, String>();

			Enumeration<String> initParameterNames = portletConfig.getInitParameterNames();

			if (initParameterNames != null) {
				int defaultViewIdLength = DEFAULT_VIEWID.length();
				int portletModeIndex = defaultViewIdLength + 1;

				while (initParameterNames.hasMoreElements()) {
					String initParameterName = initParameterNames.nextElement();

					if ((initParameterName != null) && initParameterName.startsWith(DEFAULT_VIEWID) &&
							(initParameterName.length() > defaultViewIdLength)) {
						String initParameterValue = portletConfig.getInitParameter(initParameterName);
						String portletMode = initParameterName.substring(portletModeIndex);
						defaultViewIdMap.put(portletMode, initParameterValue);
					}
				}
			}
		}

		return defaultViewIdMap;
	}

	public Bridge getFacesBridge(PortletRequest portletRequest, PortletResponse portletResponse)
		throws PortletException {
		String viewId = portletRequest.getParameter(Bridge.FACES_VIEW_ID_PARAMETER);

		if (viewId != null) {
			portletRequest.setAttribute(Bridge.VIEW_ID, viewId);
		}
		else {
			String viewPath = portletRequest.getParameter(Bridge.FACES_VIEW_PATH_PARAMETER);

			if (viewPath != null) {
				portletRequest.setAttribute(Bridge.VIEW_PATH, viewPath);
			}
		}

		return getFacesBridge();
	}

	protected Bridge getFacesBridge() throws PortletException {

		if (bridge == null) {
			BridgeFactoryFinder.setPortletConfig(portletConfig);

			BridgeFactoryFinder bridgeFactoryFinder = BridgeFactoryFinder.getInstance();
			BridgeFactory bridgeFactory = (BridgeFactory) bridgeFactoryFinder.getFactory(
					BridgeFactoryFinder.BRIDGE_FACTORY);
			PortletContext portletContext = portletConfig.getPortletContext();
			bridge = bridgeFactory.getBridge(portletContext);
		}

		return bridge;
	}

	/**
	 * @deprecated as of Bridge Spec version 2.0
	 */
	@Deprecated
	public String getResponseCharacterSetEncoding(PortletRequest request) {

		// PROPOSED-FOR-STANDARD: https://issues.apache.org/jira/browse/PORTLETBRIDGE-210
		// Since this is deprecated, proposal is to remove it entirely for the Bridge 3.0.0 Spec.
		return null;
	}

	/**
	 * @deprecated as of Bridge Spec version 2.0
	 */
	@Deprecated
	public String getResponseContentType(PortletRequest request) {

		// PROPOSED-FOR-STANDARD: https://issues.apache.org/jira/browse/PORTLETBRIDGE-210
		// Since this is deprecated, proposal is to remove it entirely for the Bridge 3.0.0 Spec.
		return request.getResponseContentType();
	}
}
