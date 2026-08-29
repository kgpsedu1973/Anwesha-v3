/**
 * Anwesha School Management System - Google Apps Script Sync Backend
 *
 * Lightweight, Multi-User, Real-Time Sync & Google Drive Backup Engine
 * 
 * Deployment Instructions:
 * 1. Open Google Drive (Admin Account) -> New -> More -> Google Apps Script
 * 2. Paste this complete script into Code.gs
 * 3. Click Deploy -> New Deployment -> Select type: 'Web app'
 *    - Description: School Sync Backend API
 *    - Execute as: Me (your Google email)
 *    - Who has access: Anyone (or Anyone with Google account)
 * 4. Click Deploy and copy the Web App URL (starts with https://script.google.com/macros/s/...)
 * 5. Paste the URL into the Android App under Settings -> Cloud Sync Settings.
 */

const SCRIPT_PROP = PropertiesService.getScriptProperties();
const MASTER_FILE_NAME = "school_database_master.json";
const BACKUPS_FOLDER_NAME = "Anwesha_School_Backups";
const USERS_FILE_NAME = "authorized_users.json";

function doGet(e) {
  return handleRequest(e);
}

function doPost(e) {
  return handleRequest(e);
}

function handleRequest(e) {
  const lock = LockService.getScriptLock();
  try {
    lock.waitLock(15000); // 15 sec concurrency lock
  } catch (err) {
    return jsonResponse({ status: "error", message: "Server busy, please retry in a moment" }, 503);
  }

  try {
    let params = {};
    if (e && e.postData && e.postData.contents) {
      try {
        params = JSON.parse(e.postData.contents);
      } catch (ex) {
        params = e.parameter || {};
      }
    } else if (e && e.parameter) {
      params = e.parameter;
    }

    const action = params.action || "get_status";
    const userEmail = (params.userEmail || "").trim().toLowerCase();

    // 1. Authorization Check
    const authResult = checkUserAuthorization(userEmail);
    if (!authResult.authorized && action !== "get_status" && action !== "initial_setup") {
      return jsonResponse({
        status: "unauthorized",
        message: "আপনার ইমেইল (" + userEmail + ") অনুমোদিত নয়। দয়া করে বিদ্যালয়ের অ্যাডমিনের সাথে যোগাযোগ করুন।"
      }, 403);
    }

    // 2. Action Router
    switch (action) {
      case "get_status":
        return getStatusHandler(userEmail, authResult);

      case "initial_setup":
        return initialSetupHandler(params, userEmail);

      case "sync_push":
        return syncPushHandler(params, userEmail, authResult);

      case "sync_pull":
        return syncPullHandler(params, userEmail, authResult);

      case "backup_create":
        return backupCreateHandler(params, userEmail, authResult);

      case "backup_list":
        return backupListHandler(params, userEmail, authResult);

      case "backup_restore":
        return backupRestoreHandler(params, userEmail, authResult);

      case "user_list":
        return userListHandler(params, userEmail, authResult);

      case "user_save":
        return userSaveHandler(params, userEmail, authResult);

      case "user_delete":
        return userDeleteHandler(params, userEmail, authResult);

      default:
        return jsonResponse({ status: "error", message: "অজানা অ্যাকশন: " + action }, 400);
    }
  } catch (error) {
    return jsonResponse({ status: "error", message: error.toString() }, 500);
  } finally {
    lock.releaseLock();
  }
}

// --------------------------------------------------------------------------
// STORAGE HELPERS (Google Drive)
// --------------------------------------------------------------------------

function getOrCreateBackupFolder() {
  const folders = DriveApp.getFoldersByName(BACKUPS_FOLDER_NAME);
  if (folders.hasNext()) {
    return folders.next();
  }
  return DriveApp.createFolder(BACKUPS_FOLDER_NAME);
}

function getMasterDatabaseFile() {
  const folder = getOrCreateBackupFolder();
  const files = folder.getFilesByName(MASTER_FILE_NAME);
  if (files.hasNext()) {
    return files.next();
  }
  // If not found in folder, search root
  const rootFiles = DriveApp.getFilesByName(MASTER_FILE_NAME);
  if (rootFiles.hasNext()) {
    return rootFiles.next();
  }
  return null;
}

function getMasterDatabase() {
  const file = getMasterDatabaseFile();
  if (!file) {
    return {
      schemaVersion: 1,
      globalDatabaseVersion: 1,
      lastUpdated: new Date().getTime(),
      schoolInfo: { schoolName: "অন্বেষা বিদ্যালয়", eiinCode: "123456" },
      usersList: [],
      studentsList: [],
      attendanceList: [],
      examResultsList: [],
      routineList: [],
      customFieldsList: [],
      formulaRulesList: []
    };
  }
  try {
    const content = file.getBlob().getDataAsString();
    return JSON.parse(content);
  } catch (e) {
    return { schemaVersion: 1, globalDatabaseVersion: 1, lastUpdated: new Date().getTime() };
  }
}

function saveMasterDatabase(dbData) {
  const folder = getOrCreateBackupFolder();
  dbData.lastUpdated = new Date().getTime();
  if (!dbData.globalDatabaseVersion) dbData.globalDatabaseVersion = 1;
  
  const content = JSON.stringify(dbData, null, 2);
  const existingFile = getMasterDatabaseFile();
  if (existingFile) {
    existingFile.setContent(content);
    return existingFile.getId();
  } else {
    const newFile = folder.createFile(MASTER_FILE_NAME, content, MimeType.PLAIN_TEXT);
    return newFile.getId();
  }
}

// --------------------------------------------------------------------------
// USER AUTHORIZATION & RBAC
// --------------------------------------------------------------------------

function getAuthorizedUsers() {
  const folder = getOrCreateBackupFolder();
  const files = folder.getFilesByName(USERS_FILE_NAME);
  if (!files.hasNext()) {
    const adminEmail = Session.getEffectiveUser().getEmail().toLowerCase();
    const defaultUsers = [
      {
        email: adminEmail,
        displayName: "School Administrator",
        role: "Admin",
        status: "Active",
        canViewStudents: true,
        canEditStudents: true,
        canDeleteStudents: true,
        canViewAttendance: true,
        canEditAttendance: true,
        canViewExamResults: true,
        canEditExamResults: true,
        canManageSettings: true,
        canManageUsers: true,
        canBackupRestore: true,
        restrictedFieldsJson: "[]",
        addedBy: "system",
        addedAt: new Date().getTime()
      }
    ];
    folder.createFile(USERS_FILE_NAME, JSON.stringify(defaultUsers, null, 2), MimeType.PLAIN_TEXT);
    return defaultUsers;
  }
  try {
    const content = files.next().getBlob().getDataAsString();
    return JSON.parse(content);
  } catch (e) {
    return [];
  }
}

function saveAuthorizedUsers(usersList) {
  const folder = getOrCreateBackupFolder();
  const files = folder.getFilesByName(USERS_FILE_NAME);
  const content = JSON.stringify(usersList, null, 2);
  if (files.hasNext()) {
    files.next().setContent(content);
  } else {
    folder.createFile(USERS_FILE_NAME, content, MimeType.PLAIN_TEXT);
  }
}

function checkUserAuthorization(email) {
  if (!email) return { authorized: false, role: "Guest", permissions: {} };
  const adminEmail = Session.getEffectiveUser().getEmail().toLowerCase();
  if (email === adminEmail) {
    return {
      authorized: true,
      role: "Admin",
      permissions: {
        canViewStudents: true,
        canEditStudents: true,
        canDeleteStudents: true,
        canViewAttendance: true,
        canEditAttendance: true,
        canViewExamResults: true,
        canEditExamResults: true,
        canManageSettings: true,
        canManageUsers: true,
        canBackupRestore: true
      }
    };
  }

  const users = getAuthorizedUsers();
  const user = users.find(function(u) { return (u.email || "").toLowerCase() === email; });
  if (!user || user.status !== "Active") {
    return { authorized: false, role: "Guest", permissions: {} };
  }

  return {
    authorized: true,
    role: user.role || "Teacher",
    user: user,
    permissions: {
      canViewStudents: user.canViewStudents !== false,
      canEditStudents: user.canEditStudents !== false,
      canDeleteStudents: !!user.canDeleteStudents,
      canViewAttendance: user.canViewAttendance !== false,
      canEditAttendance: user.canEditAttendance !== false,
      canViewExamResults: user.canViewExamResults !== false,
      canEditExamResults: user.canEditExamResults !== false,
      canManageSettings: !!user.canManageSettings,
      canManageUsers: !!user.canManageUsers,
      canBackupRestore: !!user.canBackupRestore
    }
  };
}

// --------------------------------------------------------------------------
// REQUEST HANDLERS
// --------------------------------------------------------------------------

function getStatusHandler(userEmail, authResult) {
  const db = getMasterDatabase();
  return jsonResponse({
    status: "success",
    serverTime: new Date().getTime(),
    globalDatabaseVersion: db.globalDatabaseVersion || 1,
    lastUpdated: db.lastUpdated || 0,
    authorized: authResult.authorized,
    role: authResult.role,
    permissions: authResult.permissions || {},
    recordSummary: {
      studentsCount: (db.studentsList || []).filter(function(s) { return !s.isDeleted; }).length,
      attendanceCount: (db.attendanceList || []).filter(function(a) { return !a.isDeleted; }).length,
      examResultsCount: (db.examResultsList || []).filter(function(e) { return !e.isDeleted; }).length
    }
  });
}

function initialSetupHandler(params, userEmail) {
  const adminEmail = Session.getEffectiveUser().getEmail().toLowerCase();
  const users = getAuthorizedUsers();
  return jsonResponse({
    status: "success",
    adminEmail: adminEmail,
    usersCount: users.length,
    message: "Google Apps Script Backend প্রস্তুত আছে।"
  });
}

function syncPushHandler(params, userEmail, authResult) {
  const changes = params.changes || [];
  if (!changes.length) {
    return jsonResponse({ status: "success", appliedCount: 0, message: "কোনো পরিবর্তন পাওয়া যায়নি" });
  }

  const db = getMasterDatabase();
  let appliedCount = 0;
  const conflictLogs = [];

  for (let i = 0; i < changes.length; i++) {
    const item = changes[i];
    const entityType = item.entityType; // "STUDENT", "ATTENDANCE", "EXAM_RESULT", etc.
    const action = item.action; // "CREATE", "UPDATE", "DELETE"
    const payload = item.payloadJson ? JSON.parse(item.payloadJson) : (item.payload || {});
    payload.updatedBy = userEmail;
    payload.updatedAt = item.clientTimestamp || new Date().getTime();

    if (entityType === "STUDENT") {
      if (!authResult.permissions.canEditStudents && action !== "DELETE") continue;
      if (!authResult.permissions.canDeleteStudents && action === "DELETE") continue;
      if (!db.studentsList) db.studentsList = [];
      appliedCount += applyRecordPatch(db.studentsList, "studentId", payload, action, conflictLogs);
    } else if (entityType === "ATTENDANCE") {
      if (!authResult.permissions.canEditAttendance) continue;
      if (!db.attendanceList) db.attendanceList = [];
      appliedCount += applyRecordPatch(db.attendanceList, "id", payload, action, conflictLogs);
    } else if (entityType === "EXAM_RESULT") {
      if (!authResult.permissions.canEditExamResults) continue;
      if (!db.examResultsList) db.examResultsList = [];
      appliedCount += applyRecordPatch(db.examResultsList, "id", payload, action, conflictLogs);
    } else if (entityType === "SCHOOL_INFO") {
      if (authResult.permissions.canManageSettings) {
        db.schoolInfo = Object.assign(db.schoolInfo || {}, payload);
        appliedCount++;
      }
    } else if (entityType === "ROUTINE") {
      if (!db.routineList) db.routineList = [];
      appliedCount += applyRecordPatch(db.routineList, "id", payload, action, conflictLogs);
    }
  }

  saveMasterDatabase(db);

  return jsonResponse({
    status: "success",
    appliedCount: appliedCount,
    globalDatabaseVersion: db.globalDatabaseVersion,
    serverTimestamp: new Date().getTime(),
    conflicts: conflictLogs
  });
}

function applyRecordPatch(list, idKey, payload, action, conflictLogs) {
  const id = payload[idKey] || payload.id;
  if (!id) return 0;

  const index = list.findIndex(function(item) {
    return (item[idKey] || item.id) === id;
  });

  if (action === "DELETE") {
    if (index >= 0) {
      list[index].isDeleted = true;
      list[index].updatedAt = payload.updatedAt || new Date().getTime();
      list[index].updatedBy = payload.updatedBy || "";
    } else {
      payload.isDeleted = true;
      list.push(payload);
    }
    return 1;
  }

  if (index >= 0) {
    const existing = list[index];
    // Smart merge fields
    const updated = Object.assign({}, existing, payload);
    updated.version = (existing.version || 1) + 1;
    updated.isDeleted = false;
    list[index] = updated;
  } else {
    payload.version = payload.version || 1;
    payload.isDeleted = false;
    list.push(payload);
  }
  return 1;
}

function syncPullHandler(params, userEmail, authResult) {
  const sinceTimestamp = parseInt(params.sinceTimestamp || "0", 10);
  const clientGlobalVersion = parseInt(params.clientGlobalVersion || "0", 10);
  const db = getMasterDatabase();

  const serverGlobalVersion = db.globalDatabaseVersion || 1;
  const isFullRefreshRequired = clientGlobalVersion > 0 && clientGlobalVersion < serverGlobalVersion;

  if (isFullRefreshRequired) {
    return jsonResponse({
      status: "full_refresh_required",
      globalDatabaseVersion: serverGlobalVersion,
      serverTimestamp: new Date().getTime(),
      masterDatabase: db
    });
  }

  // Filter modified records
  const modifiedStudents = (db.studentsList || []).filter(function(s) {
    return (s.updatedAt || 0) >= sinceTimestamp;
  });
  const modifiedAttendance = (db.attendanceList || []).filter(function(a) {
    return (a.updatedAt || 0) >= sinceTimestamp;
  });
  const modifiedResults = (db.examResultsList || []).filter(function(r) {
    return (r.updatedAt || 0) >= sinceTimestamp;
  });
  const modifiedRoutine = (db.routineList || []).filter(function(rt) {
    return (rt.updatedAt || 0) >= sinceTimestamp;
  });

  return jsonResponse({
    status: "success",
    serverTimestamp: new Date().getTime(),
    globalDatabaseVersion: serverGlobalVersion,
    students: modifiedStudents,
    attendance: modifiedAttendance,
    examResults: modifiedResults,
    routine: modifiedRoutine,
    schoolInfo: db.schoolInfo
  });
}

function backupCreateHandler(params, userEmail, authResult) {
  if (!authResult.permissions.canBackupRestore && authResult.role !== "Admin") {
    return jsonResponse({ status: "error", message: "ব্যাকআপ নেওয়ার অনুমতি নেই" }, 403);
  }

  const db = getMasterDatabase();
  const folder = getOrCreateBackupFolder();
  const now = new Date();
  const timeStr = Utilities.formatDate(now, Session.getScriptTimeZone(), "yyyy-MM-dd_HH-mm-ss");
  const fileName = "school_backup_" + timeStr + ".json";
  
  const content = JSON.stringify(db, null, 2);
  const file = folder.createFile(fileName, content, MimeType.PLAIN_TEXT);

  return jsonResponse({
    status: "success",
    backupId: file.getId(),
    fileName: fileName,
    timestamp: now.getTime(),
    fileSize: content.length,
    message: "Google Drive এ সফলভাবে ব্যাকআপ ফাইল সংরক্ষিত হয়েছে!"
  });
}

function backupListHandler(params, userEmail, authResult) {
  const folder = getOrCreateBackupFolder();
  const files = folder.getFiles();
  const backups = [];

  while (files.hasNext()) {
    const file = files.next();
    const name = file.getName();
    if (name.startsWith("school_backup_") || name === MASTER_FILE_NAME) {
      backups.push({
        id: file.getId(),
        name: name,
        size: file.getSize(),
        createdTime: file.getDateCreated().getTime(),
        lastUpdated: file.getLastUpdated().getTime()
      });
    }
  }

  backups.sort(function(a, b) { return b.lastUpdated - a.lastUpdated; });
  return jsonResponse({ status: "success", backups: backups });
}

function backupRestoreHandler(params, userEmail, authResult) {
  if (authResult.role !== "Admin" && !authResult.permissions.canBackupRestore) {
    return jsonResponse({ status: "error", message: "শুধুমাত্র প্রধান অ্যাডমিন ডাটাবেস রিস্টোর করতে পারবেন" }, 403);
  }

  const backupFileId = params.backupFileId;
  if (!backupFileId) {
    return jsonResponse({ status: "error", message: "কোনো ব্যাকআপ ফাইল আইডি দেওয়া হয়নি" }, 400);
  }

  // 1. Safety snapshot before restore
  const currentDb = getMasterDatabase();
  const folder = getOrCreateBackupFolder();
  const safetyTimeStr = Utilities.formatDate(new Date(), Session.getScriptTimeZone(), "yyyy-MM-dd_HH-mm-ss");
  folder.createFile("pre_restore_safety_" + safetyTimeStr + ".json", JSON.stringify(currentDb, null, 2), MimeType.PLAIN_TEXT);

  // 2. Load and validate target backup
  const targetFile = DriveApp.getFileById(backupFileId);
  const targetContent = targetFile.getBlob().getDataAsString();
  const targetDb = JSON.parse(targetContent);

  // 3. Increment global version so all client devices auto-refresh cleanly
  targetDb.globalDatabaseVersion = (currentDb.globalDatabaseVersion || 1) + 1;
  targetDb.lastUpdated = new Date().getTime();

  saveMasterDatabase(targetDb);

  return jsonResponse({
    status: "success",
    newGlobalVersion: targetDb.globalDatabaseVersion,
    restoredStudentsCount: (targetDb.studentsList || []).length,
    message: "সফলভাবে ডাটাবেস রিস্টোর সম্পন্ন হয়েছে! সমস্ত ডিভাইসে আপডেট পৌঁছে যাবে।"
  });
}

function userListHandler(params, userEmail, authResult) {
  const users = getAuthorizedUsers();
  return jsonResponse({ status: "success", users: users });
}

function userSaveHandler(params, userEmail, authResult) {
  if (authResult.role !== "Admin" && !authResult.permissions.canManageUsers) {
    return jsonResponse({ status: "error", message: "ব্যবহারকারী পরিবর্তনের অনুমতি নেই" }, 403);
  }

  const userData = params.user;
  if (!userData || !userData.email) {
    return jsonResponse({ status: "error", message: "সঠিক ইমেইল দিন" }, 400);
  }

  userData.email = userData.email.trim().toLowerCase();
  const users = getAuthorizedUsers();
  const idx = users.findIndex(function(u) { return u.email === userData.email; });

  if (idx >= 0) {
    users[idx] = Object.assign(users[idx], userData);
  } else {
    userData.addedBy = userEmail;
    userData.addedAt = new Date().getTime();
    users.push(userData);
  }

  saveAuthorizedUsers(users);
  return jsonResponse({ status: "success", user: userData, message: "ব্যবহারকারী সংরক্ষিত হয়েছে" });
}

function userDeleteHandler(params, userEmail, authResult) {
  if (authResult.role !== "Admin" && !authResult.permissions.canManageUsers) {
    return jsonResponse({ status: "error", message: "ব্যবহারকারী অপসারণের অনুমতি নেই" }, 403);
  }

  const targetEmail = (params.targetEmail || "").trim().toLowerCase();
  const adminEmail = Session.getEffectiveUser().getEmail().toLowerCase();
  if (targetEmail === adminEmail) {
    return jsonResponse({ status: "error", message: "মূল অ্যাডমিনকে মুছে ফেলা যাবে না" }, 400);
  }

  let users = getAuthorizedUsers();
  users = users.filter(function(u) { return u.email !== targetEmail; });
  saveAuthorizedUsers(users);

  return jsonResponse({ status: "success", message: "ব্যবহারকারীকে তালিকা থেকে বাদ দেওয়া হয়েছে" });
}

function jsonResponse(data, statusCode) {
  const output = ContentService.createTextOutput(JSON.stringify(data));
  output.setMimeType(ContentService.MimeType.JSON);
  return output;
}
