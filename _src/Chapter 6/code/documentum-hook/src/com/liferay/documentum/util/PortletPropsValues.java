/**
 * Copyright (c) 2000-2010 Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.documentum.util;

import com.liferay.util.portlet.PortletProps;

/**
 * <a href="PortletPropsValues.java.html"><b><i>View Source</i></b></a>
 *
 * @author Brian Wing Shun Chan
 *
 */
public class PortletPropsValues {

	public static final String CABINET = PortletProps.get("dfc.cabinet");

	public static final String CABINET_ROOT_FOLDER = PortletProps.get(
		"dfc.cabinet.root.folder");

	public static final String REPOSITORY = PortletProps.get(
		"dfc.repository.name");

	public static final String REPOSITORY_PASSWORD = PortletProps.get(
		"dfc.repository.password");

	public static final String REPOSITORY_USERNAME = PortletProps.get(
		"dfc.repository.username");

}