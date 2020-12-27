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

package com.liferay.documentum.hook;

import com.documentum.com.DfClientX;
import com.documentum.fc.client.IDfClient;
import com.documentum.fc.client.IDfCollection;
import com.documentum.fc.client.IDfDocument;
import com.documentum.fc.client.IDfFolder;
import com.documentum.fc.client.IDfSession;
import com.documentum.fc.client.IDfSessionManager;
import com.documentum.fc.common.DfException;
import com.documentum.fc.common.DfId;
import com.documentum.fc.common.IDfLoginInfo;

import com.liferay.documentlibrary.DuplicateDirectoryException;
import com.liferay.documentlibrary.DuplicateFileException;
import com.liferay.documentlibrary.NoSuchDirectoryException;
import com.liferay.documentlibrary.NoSuchFileException;
import com.liferay.documentlibrary.util.BaseHook;
import com.liferay.documentlibrary.util.DLIndexerUtil;
import com.liferay.documentum.util.DocumentumConstants;
import com.liferay.documentum.util.PortletPropsValues;
import com.liferay.portal.PortalException;
import com.liferay.portal.SystemException;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.search.Document;
import com.liferay.portal.kernel.search.Field;
import com.liferay.portal.kernel.search.SearchEngineUtil;
import com.liferay.portal.kernel.search.SearchException;
import com.liferay.portal.kernel.util.FileUtil;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.StringPool;
import com.liferay.portal.kernel.util.StringUtil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * <a href="DocumentumHook.java.html"><b><i>View Source</i></b></a>
 *
 * @author Amos Fong
 */
public class DocumentumHook extends BaseHook {

	public DocumentumHook() {
		try {
			initSessionManager();
		}
		catch (Exception e) {
			_log.error(e, e);
		}
	}

	public void addDirectory(long companyId, long repositoryId, String dirName)
		throws PortalException, SystemException {

		IDfSession session = null;

		try {
			session = getSession();

			String folderDir = getFolderDir(companyId, repositoryId, dirName);

			IDfFolder folder = session.getFolderByPath(folderDir);

			if (folder != null) {
				throw new DuplicateDirectoryException();
			}

			addFolder(session, folderDir);
		}
		catch (DfException dfe) {
			throw new SystemException(dfe);
		}
		finally {
			if (session != null) {
				_sessionManager.release(session);
			}
		}
	}

	public void addFile(
			long companyId, String portletId, long groupId, long repositoryId,
			String fileName, long fileEntryId, String properties,
			Date modifiedDate, String[] tagsCategories, String[] tagsEntries,
			InputStream is)
		throws PortalException, SystemException {

		ByteArrayOutputStream baos = null;

		IDfSession session = null;

		try {
			session = getSession();

			String documentDir = getDocumentDir(
				companyId, repositoryId, fileName);

			if (session.getObjectByPath(documentDir) != null) {
				throw new DuplicateFileException();
			}

			String parentDir = FileUtil.getPath(documentDir);

			addFolder(session, parentDir);

			IDfDocument document = (IDfDocument)session.newObject(
				DocumentumConstants.DOCUMENT);

			document.setObjectName(FileUtil.getShortFileName(fileName));
			document.setContentType(FileUtil.getExtension(fileName));

			baos = convert(is);

			document.setContent(baos);
			document.link(parentDir);

			document.save();

			DLIndexerUtil.addFile(
				companyId, portletId, groupId, 0, repositoryId, fileName,
				fileEntryId, properties, modifiedDate, tagsCategories,
				tagsEntries);
		}
		catch (DfException dfe) {
			throw new SystemException(dfe);
		}
		catch (IOException ioe) {
			throw new SystemException(ioe);
		}
		finally {
			try {
				if (baos != null) {
					baos.close();
				}
			}
			catch (IOException ioe) {
			}

			if (session != null) {
				_sessionManager.release(session);
			}
		}
	}

	public void checkRoot(long companyId) {
	}

	public void deleteDirectory(
			long companyId, String portletId, long repositoryId, String dirName)
		throws PortalException, SystemException {

		IDfSession session = null;

		try {
			session = getSession();

			String folderDir = getFolderDir(companyId, repositoryId, dirName);

			IDfFolder folder = session.getFolderByPath(folderDir);

			if (folder == null) {
				throw new NoSuchDirectoryException();
			}

			deleteFolder(session, companyId, portletId, repositoryId, folder);
		}
		catch (DfException dfe) {
			throw new SystemException(dfe);
		}
		catch (SearchException se) {
			throw new SystemException(se);
		}
		finally {
			if (session != null) {
				_sessionManager.release(session);
			}
		}
	}

	public void deleteFile(
			long companyId, String portletId, long repositoryId,
			String fileName)
		throws PortalException, SystemException {

		IDfSession session = null;

		try {
			session = getSession();

			String documentDir = getDocumentDir(
				companyId, repositoryId, fileName);

			IDfDocument document = (IDfDocument)session.getObjectByPath(
				documentDir);

			if (document == null) {
				throw new NoSuchFileException();
			}

			document.destroyAllVersions();

			DLIndexerUtil.deleteFile(
				companyId, portletId, repositoryId, fileName);
		}
		catch (DfException dfe) {
			throw new SystemException(dfe);
		}
		finally {
			if (session != null) {
				_sessionManager.release(session);
			}
		}
	}

	public void deleteFile(
			long companyId, String portletId, long repositoryId,
			String fileName, double versionNumber)
		throws PortalException, SystemException {

		IDfSession session = null;

		try {
			session = getSession();

			String documentDir = getDocumentDir(
				companyId, repositoryId, fileName);

			IDfDocument document = (IDfDocument)session.getObjectByPath(
				documentDir);

			if (document == null) {
				throw new NoSuchFileException();
			}

			IDfCollection docVersions = document.getVersions(
				DocumentumConstants.OBJECT_ID);

			while (docVersions.next()) {
				DfId docId = new DfId(
					docVersions.getString(DocumentumConstants.OBJECT_ID));

				IDfDocument docVersion = (IDfDocument)session.getObject(docId);

				double docVersionNumber = GetterUtil.getDouble(
					docVersion.getVersionLabel(0));

				if (docVersionNumber ==	versionNumber) {
					docVersion.destroy();

					break;
				}
			}
		}
		catch (DfException dfe) {
			throw new SystemException(dfe);
		}
		finally {
			if (session != null) {
				_sessionManager.release(session);
			}
		}
	}

	public InputStream getFileAsStream(
			long companyId, long repositoryId, String fileName,
			double versionNumber)
		throws PortalException, SystemException {

		InputStream is = null;

		IDfSession session = null;

		try {
			session = getSession();

			String documentDir = getDocumentDir(
				companyId, repositoryId, fileName);

			IDfDocument document = (IDfDocument)session.getObjectByPath(
				documentDir);

			if (document == null) {
				throw new NoSuchFileException();
			}

			IDfCollection docVersions = document.getVersions(
				DocumentumConstants.OBJECT_ID);

			while (docVersions.next()) {
				DfId docId = new DfId(
					docVersions.getString(DocumentumConstants.OBJECT_ID));

				IDfDocument docVersion = (IDfDocument)session.getObject(docId);

				double docVersionNumber = GetterUtil.getDouble(
					docVersion.getVersionLabel(0));

				if (docVersionNumber == versionNumber) {
					is = docVersion.getContent();

					break;
				}
			}
		}
		catch (DfException dfe) {
			throw new SystemException(dfe);
		}
		finally {
			if (session != null) {
				_sessionManager.release(session);
			}
		}

		return is;
	}

	public String[] getFileNames(
			long companyId, long repositoryId, String dirName)
		throws PortalException, SystemException {

		List<String> fileNames = new ArrayList<String>();

		IDfSession session = null;

		try {
			session = getSession();

			String folderDir = getFolderDir(companyId, repositoryId, dirName);

			IDfFolder folder = session.getFolderByPath(folderDir);

			if (folder == null) {
				throw new NoSuchDirectoryException(dirName);
			}

			IDfCollection documents = folder.getContents(
				DocumentumConstants.OBJECT_NAME);

			while (documents.next()) {
				String documentName = documents.getString(
					DocumentumConstants.OBJECT_NAME);

				fileNames.add(dirName + StringPool.SLASH + documentName);
			}
		}
		catch (DfException dfe) {
			throw new SystemException(dfe);
		}
		finally {
			if (session != null) {
				_sessionManager.release(session);
			}
		}

		return fileNames.toArray(new String[fileNames.size()]);
	}

	public long getFileSize(
			long companyId, long repositoryId, String fileName)
		throws PortalException, SystemException {

		long size;

		IDfSession session = null;

		try {
			session = getSession();

			String documentDir = getDocumentDir(
				companyId, repositoryId, fileName);

			IDfDocument document = (IDfDocument)session.getObjectByPath(
				documentDir);

			if (document == null) {
				throw new NoSuchFileException();
			}

			size = document.getContentSize();
		}
		catch (DfException dfe) {
			throw new SystemException(dfe);
		}
		finally {
			if (session != null) {
				_sessionManager.release(session);
			}
		}

		return size;
	}

	public boolean hasFile(
			long companyId, long repositoryId, String fileName,
			double versionNumber)
		throws PortalException, SystemException {

		IDfSession session = null;

		try {
			session = getSession();

			String documentDir = getDocumentDir(
				companyId, repositoryId, fileName);

			IDfDocument document = (IDfDocument)session.getObjectByPath(
				documentDir);

			if (document == null) {
				return false;
			}

			return hasVersion(session, document, versionNumber);
		}
		catch (DfException dfe) {
			throw new SystemException(dfe);
		}
		finally {
			if (session != null) {
				_sessionManager.release(session);
			}
		}
	}

	public void move(String srcDir, String destDir) {
	}

	public void reIndex(String[] ids) throws SearchException {
		long companyId = GetterUtil.getLong(ids[0]);
		String portletId = ids[1];
		long groupId = GetterUtil.getLong(ids[2]);
		long repositoryId = GetterUtil.getLong(ids[3]);

		IDfSession session = null;

		try {
			session = getSession();

			String repositoryDir = getRepositoryDir(companyId, repositoryId);

			IDfFolder folder = session.getFolderByPath(repositoryDir);

			if (folder == null) {
				return;
			}

			IDfCollection documents = folder.getContents(
				DocumentumConstants.OBJECT_NAME);

			while (documents.next()) {
				String documentName = documents.getString(
					DocumentumConstants.OBJECT_NAME);

				try {
					Document doc = DLIndexerUtil.getFileDocument(
						companyId, portletId, groupId, 0, repositoryId,
						documentName);

					SearchEngineUtil.updateDocument(
						companyId, doc.get(Field.UID), doc);
				}
				catch (Exception e2) {
					_log.error("Reindexing " + documentName, e2);
				}
			}
		}
		catch (Exception e1) {
			throw new SearchException(e1);
		}
		finally {
			if (session != null) {
				_sessionManager.release(session);
			}
		}
	}

	public void updateFile(
			long companyId, String portletId, long groupId, long repositoryId,
			long newRepositoryId, String fileName, long fileEntryId)
		throws PortalException, SystemException {

		IDfSession session = null;

		try {
			session = getSession();

			String documentDir = getDocumentDir(
				companyId, repositoryId, fileName);

			IDfDocument document = (IDfDocument)session.getObjectByPath(
				documentDir);

			if (document == null) {
				throw new NoSuchFileException();
			}

			String repositoryDir = getRepositoryDir(companyId, repositoryId);
			String newRepositoryDir = getRepositoryDir(
				companyId, newRepositoryId);

			addFolder(session, newRepositoryDir);

			document.unlink(repositoryDir);
			document.link(newRepositoryDir);

			document.save();

			try {
				DLIndexerUtil.deleteFile(
					companyId, portletId, repositoryId, fileName);
			}
			catch (SearchException se) {
			}

			DLIndexerUtil.addFile(
				companyId, portletId, groupId, 0, newRepositoryId, fileName);
		}
		catch (DfException dfe) {
			throw new SystemException(dfe);
		}
		catch (SearchException se) {
			throw new SystemException(se);
		}
		finally {
			if (session != null) {
				_sessionManager.release(session);
			}
		}
	}

	public void updateFile(
			long companyId, String portletId, long groupId, long repositoryId,
			String fileName, String newFileName, boolean reIndex)
		throws PortalException, SystemException {

		IDfSession session = null;

		try {
			session = getSession();

			String documentDir = getDocumentDir(
				companyId, repositoryId, fileName);

			IDfDocument document = (IDfDocument)session.getObjectByPath(
				documentDir);

			if (document == null) {
				throw new NoSuchFileException();
			}

			document.checkout();
			document.setObjectName(FileUtil.getShortFileName(newFileName));

			document.checkin(false, null);

			document.save();

			if (reIndex) {
				try {
					DLIndexerUtil.deleteFile(
						companyId, portletId, repositoryId, fileName);
				}
				catch (SearchException se) {
				}

				DLIndexerUtil.addFile(
					companyId, portletId, groupId, 0, repositoryId, newFileName);
			}
		}
		catch (DfException dfe) {
			throw new SystemException(dfe);
		}
		catch (SearchException se) {
			throw new SystemException(se);
		}
		finally {
			if (session != null) {
				_sessionManager.release(session);
			}
		}
	}

	public void updateFile(
			long companyId, String portletId, long groupId, long repositoryId,
			String fileName, double versionNumber, String sourceFileName,
			long fileEntryId, String properties, Date modifiedDate,
			String[] tagsCategories, String[] tagsEntries, InputStream is)
		throws PortalException, SystemException {

		ByteArrayOutputStream baos = null;

		IDfSession session = null;

		try {
			session = getSession();

			String documentDir = getDocumentDir(
				companyId, repositoryId, fileName);

			IDfDocument document = (IDfDocument)session.getObjectByPath(
				documentDir);

			if (document == null) {
				throw new NoSuchFileException();
			}

			if (hasVersion(session, document, versionNumber)) {
				throw new DuplicateFileException();
			}

			document.checkout();
			document.setContentType(FileUtil.getExtension(fileName));

			baos = convert(is);

			document.setContent(baos);

			String newVersion =
				String.valueOf(versionNumber) + StringPool.COMMA +
					DocumentumConstants.CURRENT;

			document.checkin(false, newVersion);

			document.save();

			DLIndexerUtil.updateFile(
				companyId, portletId, groupId, 0, repositoryId, fileName,
				fileEntryId, properties, modifiedDate, tagsCategories,
				tagsEntries);
		}
		catch (DfException dfe) {
			throw new SystemException(dfe);
		}
		catch (IOException ioe) {
			throw new SystemException(ioe);
		}
		catch (SearchException se) {
			throw new SystemException(se);
		}
		finally {
			try {
				if (baos != null) {
					baos.close();
				}
			}
			catch (IOException ioe) {
			}

			if (session != null) {
				_sessionManager.release(session);
			}
		}
	}

	protected void addFolder(IDfSession session, String folderDir)
		throws DfException {

		String[] folderNames = StringUtil.split(
			folderDir.substring(PortletPropsValues.CABINET.length() + 1),
			StringPool.SLASH);

		String parentFolderDir = PortletPropsValues.CABINET;

		for (String curFolderName : folderNames) {
			String curFolderDir =
				parentFolderDir + StringPool.SLASH + curFolderName;

			IDfFolder folder = session.getFolderByPath(curFolderDir);

			if (folder == null) {
				folder = (IDfFolder)session.newObject(
					DocumentumConstants.FOLDER);

				folder.setObjectName(curFolderName);
				folder.link(parentFolderDir);

				folder.save();
			}

			parentFolderDir = curFolderDir;
		}
	}

	protected ByteArrayOutputStream convert(InputStream is) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();

		baos.write(FileUtil.getBytes(is));

		return baos;
	}

	protected void deleteFolder(
			IDfSession session, long companyId, String portletId,
			long repositoryId, IDfFolder folder)
		throws DfException, SearchException {

		IDfCollection objects = folder.getContents(
			DocumentumConstants.OBJECT_ATTRIBUTES);

		while (objects.next()) {
			DfId id = new DfId(
				objects.getString(DocumentumConstants.OBJECT_ID));
			String type = objects.getString(DocumentumConstants.OBJECT_TYPE);

			if (type.equals(DocumentumConstants.DOCUMENT)) {
				IDfDocument document = (IDfDocument)session.getObject(id);

				document.destroyAllVersions();

				DLIndexerUtil.deleteFile(
					companyId, portletId, repositoryId,
					objects.getString(DocumentumConstants.OBJECT_NAME));
			}
			else if (type.equals(DocumentumConstants.FOLDER)) {
				deleteFolder(
					session, companyId, portletId, repositoryId,
					(IDfFolder)session.getObject(id));
			}
		}

		folder.destroyAllVersions();
	}

	protected String getCompanyDir(long companyId)
		throws PortalException, SystemException {

		StringBuilder sb = new StringBuilder();

		sb.append(PortletPropsValues.CABINET);
		sb.append(PortletPropsValues.CABINET_ROOT_FOLDER);
		sb.append(StringPool.SLASH);
		sb.append(companyId);

		return sb.toString();
	}

	protected String getDocumentDir(
			long companyId, long repositoryId, String fileName)
		throws PortalException, SystemException {

		String repositoryDir = getRepositoryDir(companyId, repositoryId);

		return repositoryDir + StringPool.SLASH + fileName;
	}

	protected String getFolderDir(
			long companyId, long repositoryId, String folderDir)
		throws PortalException, SystemException {

		return getDocumentDir(companyId, repositoryId, folderDir);
	}

	protected String getRepositoryDir(long companyId, long repositoryId)
		throws PortalException, SystemException {

		String companyDir = getCompanyDir(companyId);

		return companyDir + StringPool.SLASH + repositoryId;
	}

	protected IDfSession getSession() throws DfException {
		return _sessionManager.getSession(PortletPropsValues.REPOSITORY);
	}

	protected boolean hasVersion(
			IDfSession session, IDfDocument document, double versionNumber)
		throws DfException {

		IDfCollection docVersions = document.getVersions(
			DocumentumConstants.OBJECT_ID);

		while (docVersions.next()) {
			DfId docId = new DfId(
				docVersions.getString(DocumentumConstants.OBJECT_ID));

			IDfDocument docVersion = (IDfDocument)session.getObject(docId);

			double docVersionNumber = GetterUtil.getDouble(
				docVersion.getVersionLabel(0));

			if (docVersionNumber == versionNumber) {
				return true;
			}
		}

		return false;
	}

	protected void initSessionManager() throws DfException {
		DfClientX clientX = new DfClientX();

		IDfClient client = clientX.getLocalClient();

		_sessionManager = client.newSessionManager();

		IDfLoginInfo loginInfo = clientX.getLoginInfo();

		loginInfo.setUser(PortletPropsValues.REPOSITORY_USERNAME);
		loginInfo.setPassword(PortletPropsValues.REPOSITORY_PASSWORD);

		_sessionManager.setIdentity(PortletPropsValues.REPOSITORY, loginInfo);
	}

	private static Log _log = LogFactoryUtil.getLog(DocumentumHook.class);

	private IDfSessionManager _sessionManager;

}