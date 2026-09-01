import React, { createContext, useContext, useState, useEffect, useMemo, ReactNode } from 'react';
import {
  Student,
  SchoolInfo,
  CustomField,
  FormulaRule,
  AttendanceRecord,
  RoutineItem,
  DocumentTemplate,
  StudentDocument,
  ActiveScreen,
  ToolRoute,
  GenderTerminology
} from '../types';
import { AppStorage } from '../utils/storage';
import { FormulaEvaluator } from '../utils/formulaEvaluator';

interface ToastInfo {
  id: string;
  message: string;
  type: 'success' | 'error' | 'info';
}

interface AppContextType {
  // Navigation
  activeScreen: ActiveScreen;
  setActiveScreen: (screen: ActiveScreen) => void;
  activeTool: ToolRoute | null;
  setActiveTool: (tool: ToolRoute | null) => void;

  // School
  schoolInfo: SchoolInfo;
  updateSchoolInfo: (info: Partial<SchoolInfo>) => void;

  // Students
  students: Student[];
  filteredStudents: Student[];
  addStudent: (student: Omit<Student, 'id' | 'createdAt' | 'updatedAt'>) => void;
  updateStudent: (id: string, data: Partial<Student>) => void;
  deleteStudent: (id: string) => void;
  bulkDeleteStudents: (ids: string[]) => void;
  bulkUpdateClass: (ids: string[], newClass: string) => void;
  bulkUpdateSection: (ids: string[], newSection: string) => void;
  bulkUpdateStatus: (ids: string[], newStatus: Student['status']) => void;
  importStudents: (newStudents: Student[]) => void;

  // Custom Fields & Formulas
  customFields: CustomField[];
  addCustomField: (field: Omit<CustomField, 'id'>) => void;
  updateCustomField: (id: string, field: Partial<CustomField>) => void;
  deleteCustomField: (id: string) => void;

  formulaRules: FormulaRule[];
  addFormulaRule: (rule: Omit<FormulaRule, 'id'>) => void;
  updateFormulaRule: (id: string, rule: Partial<FormulaRule>) => void;
  deleteFormulaRule: (id: string) => void;

  // Attendance
  attendanceRecords: AttendanceRecord[];
  addAttendanceRecord: (record: Omit<AttendanceRecord, 'id' | 'createdAt'>) => void;
  updateAttendanceRecord: (id: string, record: Partial<AttendanceRecord>) => void;
  deleteAttendanceRecord: (id: string) => void;

  // Routines & Templates & Docs
  routineItems: RoutineItem[];
  addRoutineItem: (item: Omit<RoutineItem, 'id'>) => void;
  deleteRoutineItem: (id: string) => void;

  templates: DocumentTemplate[];
  studentDocuments: StudentDocument[];
  addStudentDocument: (doc: Omit<StudentDocument, 'id' | 'createdAt'>) => void;
  deleteStudentDocument: (id: string) => void;

  // Gender Terminology
  genderTerminology: GenderTerminology;
  updateGenderTerminology: (terms: GenderTerminology) => void;

  // Filtering & Search
  searchQuery: string;
  setSearchQuery: (query: string) => void;
  filterClass: string;
  setFilterClass: (cls: string) => void;
  filterGender: string;
  setFilterGender: (gender: string) => void;
  filterStatus: string;
  setFilterStatus: (status: string) => void;
  filterVillage: string;
  setFilterVillage: (village: string) => void;
  filterSpecialNeeds: string;
  setFilterSpecialNeeds: (val: string) => void;
  filterCategory: string; // "ALL" | "অভ্যন্তরীণ" | "বহিরাগত"
  setFilterCategory: (cat: string) => void;
  resetFilters: () => void;

  // Statistics
  stats: {
    totalStudents: number;
    totalBoys: number;
    totalGirls: number;
    totalSpecialNeeds: number;
    totalInternal: number;
    totalExternal: number;
    classCounts: Record<string, { total: number; boys: number; girls: number }>;
  };

  // Toast
  toasts: ToastInfo[];
  showToast: (message: string, type?: 'success' | 'error' | 'info') => void;
  removeToast: (id: string) => void;

  // Backup / Reset
  resetDatabase: () => void;
  exportDatabase: () => string;
  importDatabase: (json: string) => boolean;
}

const AppContext = createContext<AppContextType | undefined>(undefined);

export const AppProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  const [activeScreen, setActiveScreen] = useState<ActiveScreen>('dashboard');
  const [activeTool, setActiveTool] = useState<ToolRoute | null>(null);

  const [schoolInfo, setSchoolInfoState] = useState<SchoolInfo>(() => AppStorage.getSchoolInfo());
  const [students, setStudentsState] = useState<Student[]>(() => AppStorage.getStudents());
  const [customFields, setCustomFieldsState] = useState<CustomField[]>(() => AppStorage.getCustomFields());
  const [formulaRules, setFormulaRulesState] = useState<FormulaRule[]>(() => AppStorage.getFormulaRules());
  const [attendanceRecords, setAttendanceState] = useState<AttendanceRecord[]>(() => AppStorage.getAttendance());
  const [routineItems, setRoutineState] = useState<RoutineItem[]>(() => AppStorage.getRoutines());
  const [templates, setTemplatesState] = useState<DocumentTemplate[]>(() => AppStorage.getTemplates());
  const [studentDocuments, setDocumentsState] = useState<StudentDocument[]>(() => AppStorage.getDocuments());
  const [genderTerminology, setGenderTermsState] = useState<GenderTerminology>(() => AppStorage.getGenderTerminology());

  // Filter States
  const [searchQuery, setSearchQuery] = useState('');
  const [filterClass, setFilterClass] = useState('ALL');
  const [filterGender, setFilterGender] = useState('ALL');
  const [filterStatus, setFilterStatus] = useState('ALL');
  const [filterVillage, setFilterVillage] = useState('ALL');
  const [filterSpecialNeeds, setFilterSpecialNeeds] = useState('ALL');
  const [filterCategory, setFilterCategory] = useState('ALL');

  // Toasts
  const [toasts, setToasts] = useState<ToastInfo[]>([]);

  const showToast = (message: string, type: 'success' | 'error' | 'info' = 'success') => {
    const id = Date.now().toString() + Math.random().toString().slice(2, 6);
    setToasts(prev => [...prev, { id, message, type }]);
    setTimeout(() => {
      removeToast(id);
    }, 3500);
  };

  const removeToast = (id: string) => {
    setToasts(prev => prev.filter(t => t.id !== id));
  };

  // Sync helpers
  const updateSchoolInfo = (info: Partial<SchoolInfo>) => {
    setSchoolInfoState(prev => {
      const updated = { ...prev, ...info, updatedAt: Date.now() };
      AppStorage.setSchoolInfo(updated);
      return updated;
    });
    showToast('বিদ্যালয়ের তথ্য হালনাগাদ করা হয়েছে');
  };

  const addStudent = (studentData: Omit<Student, 'id' | 'createdAt' | 'updatedAt'>) => {
    const id = `STU-${new Date().getFullYear()}-${String(students.length + 1).padStart(3, '0')}`;
    const now = Date.now();
    const newStudent: Student = {
      ...studentData,
      id,
      createdAt: now,
      updatedAt: now
    };
    setStudentsState(prev => {
      const updated = [newStudent, ...prev];
      AppStorage.setStudents(updated);
      return updated;
    });
    showToast(`শিক্ষার্থী ${newStudent.name} সফলভাবে যুক্ত হয়েছে`);
  };

  const updateStudent = (id: string, data: Partial<Student>) => {
    setStudentsState(prev => {
      const updated = prev.map(s => (s.id === id ? { ...s, ...data, updatedAt: Date.now() } : s));
      AppStorage.setStudents(updated);
      return updated;
    });
    showToast('শিক্ষার্থীর তথ্য সফলভাবে হালনাগাদ করা হয়েছে');
  };

  const deleteStudent = (id: string) => {
    setStudentsState(prev => {
      const updated = prev.filter(s => s.id !== id);
      AppStorage.setStudents(updated);
      return updated;
    });
    showToast('শিক্ষার্থী তালিকা থেকে মুছে ফেলা হয়েছে', 'info');
  };

  const bulkDeleteStudents = (ids: string[]) => {
    setStudentsState(prev => {
      const updated = prev.filter(s => !ids.includes(s.id));
      AppStorage.setStudents(updated);
      return updated;
    });
    showToast(`${ids.length} জন শিক্ষার্থী মুছে ফেলা হয়েছে`, 'info');
  };

  const bulkUpdateClass = (ids: string[], newClass: string) => {
    setStudentsState(prev => {
      const updated = prev.map(s => (ids.includes(s.id) ? { ...s, studentClass: newClass, updatedAt: Date.now() } : s));
      AppStorage.setStudents(updated);
      return updated;
    });
    showToast(`${ids.length} জন শিক্ষার্থীর শ্রেণি পরিবর্তন করা হয়েছে`);
  };

  const bulkUpdateSection = (ids: string[], newSection: string) => {
    setStudentsState(prev => {
      const updated = prev.map(s => (ids.includes(s.id) ? { ...s, section: newSection, updatedAt: Date.now() } : s));
      AppStorage.setStudents(updated);
      return updated;
    });
    showToast(`${ids.length} জন শিক্ষার্থীর শাখা পরিবর্তন করা হয়েছে`);
  };

  const bulkUpdateStatus = (ids: string[], newStatus: Student['status']) => {
    setStudentsState(prev => {
      const updated = prev.map(s => (ids.includes(s.id) ? { ...s, status: newStatus, updatedAt: Date.now() } : s));
      AppStorage.setStudents(updated);
      return updated;
    });
    showToast(`${ids.length} জন শিক্ষার্থীর স্ট্যাটাস পরিবর্তন করা হয়েছে`);
  };

  const importStudents = (newStudents: Student[]) => {
    setStudentsState(prev => {
      // Merge or append
      const existingIds = new Set(prev.map(s => s.id));
      const filteredNew = newStudents.filter(s => !existingIds.has(s.id));
      const updated = [...prev, ...filteredNew];
      AppStorage.setStudents(updated);
      return updated;
    });
    showToast(`${newStudents.length} জন শিক্ষার্থী ইম্পোর্ট করা হয়েছে`);
  };

  const addCustomField = (fieldData: Omit<CustomField, 'id'>) => {
    const id = `cf_${Date.now()}`;
    const newField: CustomField = { ...fieldData, id };
    setCustomFieldsState(prev => {
      const updated = [...prev, newField];
      AppStorage.setCustomFields(updated);
      return updated;
    });
    showToast(`নতুন ফিল্ড '${newField.name}' তৈরি হয়েছে`);
  };

  const updateCustomField = (id: string, fieldData: Partial<CustomField>) => {
    setCustomFieldsState(prev => {
      const updated = prev.map(f => (f.id === id ? { ...f, ...fieldData } : f));
      AppStorage.setCustomFields(updated);
      return updated;
    });
    showToast('ফিল্ড সফলভাবে হালনাগাদ করা হয়েছে');
  };

  const deleteCustomField = (id: string) => {
    setCustomFieldsState(prev => {
      const updated = prev.filter(f => f.id !== id);
      AppStorage.setCustomFields(updated);
      return updated;
    });
    showToast('ফিল্ড মুছে ফেলা হয়েছে', 'info');
  };

  const addFormulaRule = (ruleData: Omit<FormulaRule, 'id'>) => {
    const id = `rule_${Date.now()}`;
    const newRule: FormulaRule = { ...ruleData, id };
    setFormulaRulesState(prev => {
      const updated = [...prev, newRule];
      AppStorage.setFormulaRules(updated);
      return updated;
    });
    showToast(`নতুন সূত্র নিয়ম '${newRule.ruleName}' তৈরি হয়েছে`);
  };

  const updateFormulaRule = (id: string, ruleData: Partial<FormulaRule>) => {
    setFormulaRulesState(prev => {
      const updated = prev.map(r => (r.id === id ? { ...r, ...ruleData } : r));
      AppStorage.setFormulaRules(updated);
      return updated;
    });
    showToast('সূত্র নিয়ম হালনাগাদ করা হয়েছে');
  };

  const deleteFormulaRule = (id: string) => {
    setFormulaRulesState(prev => {
      const updated = prev.filter(r => r.id !== id);
      AppStorage.setFormulaRules(updated);
      return updated;
    });
    showToast('সূত্র নিয়ম মুছে ফেলা হয়েছে', 'info');
  };

  const addAttendanceRecord = (recordData: Omit<AttendanceRecord, 'id' | 'createdAt'>) => {
    const id = `att_${Date.now()}`;
    const newRecord: AttendanceRecord = { ...recordData, id, createdAt: Date.now() };
    setAttendanceState(prev => {
      // Check if duplicate for same date and class
      const filtered = prev.filter(r => !(r.date === newRecord.date && r.className === newRecord.className));
      const updated = [newRecord, ...filtered];
      AppStorage.setAttendance(updated);
      return updated;
    });
    showToast('হাজিরা রেকর্ড সংরক্ষিত হয়েছে');
  };

  const updateAttendanceRecord = (id: string, recordData: Partial<AttendanceRecord>) => {
    setAttendanceState(prev => {
      const updated = prev.map(r => (r.id === id ? { ...r, ...recordData } : r));
      AppStorage.setAttendance(updated);
      return updated;
    });
    showToast('হাজিরা তথ্য হালনাগাদ হয়েছে');
  };

  const deleteAttendanceRecord = (id: string) => {
    setAttendanceState(prev => {
      const updated = prev.filter(r => r.id !== id);
      AppStorage.setAttendance(updated);
      return updated;
    });
    showToast('হাজিরা রেকর্ড মুছে ফেলা হয়েছে', 'info');
  };

  const addRoutineItem = (itemData: Omit<RoutineItem, 'id'>) => {
    const id = `rt_${Date.now()}`;
    const newItem: RoutineItem = { ...itemData, id };
    setRoutineState(prev => {
      const updated = [...prev, newItem];
      AppStorage.setRoutines(updated);
      return updated;
    });
    showToast('রুটিন এন্ট্রি যুক্ত করা হয়েছে');
  };

  const deleteRoutineItem = (id: string) => {
    setRoutineState(prev => {
      const updated = prev.filter(r => r.id !== id);
      AppStorage.setRoutines(updated);
      return updated;
    });
    showToast('রুটিন এন্ট্রি মুছে ফেলা হয়েছে', 'info');
  };

  const addStudentDocument = (docData: Omit<StudentDocument, 'id' | 'createdAt'>) => {
    const id = `doc_${Date.now()}`;
    const newDoc: StudentDocument = { ...docData, id, createdAt: Date.now() };
    setDocumentsState(prev => {
      const updated = [newDoc, ...prev];
      AppStorage.setDocuments(updated);
      return updated;
    });
    showToast('ডকুমেন্ট সফলভাবে সংরক্ষণ করা হয়েছে');
  };

  const deleteStudentDocument = (id: string) => {
    setDocumentsState(prev => {
      const updated = prev.filter(d => d.id !== id);
      AppStorage.setDocuments(updated);
      return updated;
    });
    showToast('ডকুমেন্ট মুছে ফেলা হয়েছে', 'info');
  };

  const updateGenderTerminology = (terms: GenderTerminology) => {
    setGenderTermsState(terms);
    AppStorage.setGenderTerminology(terms);
    showToast('লিঙ্গ পরিভাষা হালনাগাদ করা হয়েছে');
  };

  const resetFilters = () => {
    setSearchQuery('');
    setFilterClass('ALL');
    setFilterGender('ALL');
    setFilterStatus('ALL');
    setFilterVillage('ALL');
    setFilterSpecialNeeds('ALL');
    setFilterCategory('ALL');
  };

  // Filtered Students Calculation
  const filteredStudents = useMemo(() => {
    return students.filter(student => {
      // Search query matches name, roll, mobile, birth reg, parents
      if (searchQuery.trim()) {
        const q = searchQuery.trim().toLowerCase();
        const matchesName = student.name.toLowerCase().includes(q);
        const matchesRoll = String(student.rollNumber).includes(q);
        const matchesMobile = student.mobile.includes(q) || student.parentContact.includes(q);
        const matchesBirthReg = student.birthRegNumber.includes(q);
        const matchesFather = student.fatherName.toLowerCase().includes(q);
        const matchesMother = student.motherName.toLowerCase().includes(q);
        const matchesVillage = student.village.toLowerCase().includes(q);

        if (!matchesName && !matchesRoll && !matchesMobile && !matchesBirthReg && !matchesFather && !matchesMother && !matchesVillage) {
          return false;
        }
      }

      // Filter Class
      if (filterClass !== 'ALL' && student.studentClass !== filterClass) {
        return false;
      }

      // Filter Gender
      if (filterGender !== 'ALL') {
        const isBoy = student.gender === 'ছাত্র' || student.gender === 'বালক' || student.gender === 'ছেলে';
        const isGirl = student.gender === 'ছাত্রী' || student.gender === 'বালিকা' || student.gender === 'মেয়ে';
        if (filterGender === 'MALE' && !isBoy) return false;
        if (filterGender === 'FEMALE' && !isGirl) return false;
      }

      // Filter Status
      if (filterStatus !== 'ALL' && student.status !== filterStatus) {
        return false;
      }

      // Filter Village
      if (filterVillage !== 'ALL' && student.village !== filterVillage) {
        return false;
      }

      // Filter Special Needs
      if (filterSpecialNeeds !== 'ALL') {
        const hasSpecial = student.isSpecialNeeds;
        if (filterSpecialNeeds === 'YES' && !hasSpecial) return false;
        if (filterSpecialNeeds === 'NO' && hasSpecial) return false;
      }

      // Filter Category (অভ্যন্তরীণ / বহিরাগত)
      if (filterCategory !== 'ALL') {
        const category = FormulaEvaluator.getStudentCategory(student, schoolInfo.internalVillages);
        if (filterCategory !== category) return false;
      }

      return true;
    });
  }, [students, searchQuery, filterClass, filterGender, filterStatus, filterVillage, filterSpecialNeeds, filterCategory, schoolInfo.internalVillages]);

  // Statistics calculation
  const stats = useMemo(() => {
    let totalBoys = 0;
    let totalGirls = 0;
    let totalSpecialNeeds = 0;
    let totalInternal = 0;
    let totalExternal = 0;
    const classCounts: Record<string, { total: number; boys: number; girls: number }> = {};

    students.forEach(student => {
      const isBoy = student.gender === 'ছাত্র' || student.gender === 'বালক' || student.gender === 'ছেলে';
      const isGirl = student.gender === 'ছাত্রী' || student.gender === 'বালিকা' || student.gender === 'মেয়ে';

      if (isBoy) totalBoys++;
      if (isGirl) totalGirls++;
      if (student.isSpecialNeeds) totalSpecialNeeds++;

      const category = FormulaEvaluator.getStudentCategory(student, schoolInfo.internalVillages);
      if (category === 'অভ্যন্তরীণ') totalInternal++;
      else totalExternal++;

      const cls = student.studentClass || 'অন্যান্য';
      if (!classCounts[cls]) {
        classCounts[cls] = { total: 0, boys: 0, girls: 0 };
      }
      classCounts[cls].total++;
      if (isBoy) classCounts[cls].boys++;
      if (isGirl) classCounts[cls].girls++;
    });

    return {
      totalStudents: students.length,
      totalBoys,
      totalGirls,
      totalSpecialNeeds,
      totalInternal,
      totalExternal,
      classCounts
    };
  }, [students, schoolInfo.internalVillages]);

  const resetDatabase = () => {
    AppStorage.resetToDefaults();
    setSchoolInfoState(AppStorage.getSchoolInfo());
    setStudentsState(AppStorage.getStudents());
    setCustomFieldsState(AppStorage.getCustomFields());
    setFormulaRulesState(AppStorage.getFormulaRules());
    setAttendanceState(AppStorage.getAttendance());
    setRoutineState(AppStorage.getRoutines());
    setTemplatesState(AppStorage.getTemplates());
    setDocumentsState(AppStorage.getDocuments());
    setGenderTermsState(AppStorage.getGenderTerminology());
    showToast('অ্যাপ ডাটাবেজ সফলভাবে ডিফল্টে রিসেট করা হয়েছে', 'info');
  };

  const exportDatabase = () => {
    return AppStorage.exportFullBackup();
  };

  const importDatabase = (json: string) => {
    const success = AppStorage.importFullBackup(json);
    if (success) {
      setSchoolInfoState(AppStorage.getSchoolInfo());
      setStudentsState(AppStorage.getStudents());
      setCustomFieldsState(AppStorage.getCustomFields());
      setFormulaRulesState(AppStorage.getFormulaRules());
      setAttendanceState(AppStorage.getAttendance());
      setRoutineState(AppStorage.getRoutines());
      setTemplatesState(AppStorage.getTemplates());
      setDocumentsState(AppStorage.getDocuments());
      setGenderTermsState(AppStorage.getGenderTerminology());
      showToast('ডাটা ব্যাকআপ সফলভাবে পুনরুদ্ধার করা হয়েছে');
      return true;
    } else {
      showToast('ব্যাকআপ ফাইলটি সঠিক নয়', 'error');
      return false;
    }
  };

  return (
    <AppContext.Provider
      value={{
        activeScreen,
        setActiveScreen,
        activeTool,
        setActiveTool,
        schoolInfo,
        updateSchoolInfo,
        students,
        filteredStudents,
        addStudent,
        updateStudent,
        deleteStudent,
        bulkDeleteStudents,
        bulkUpdateClass,
        bulkUpdateSection,
        bulkUpdateStatus,
        importStudents,
        customFields,
        addCustomField,
        updateCustomField,
        deleteCustomField,
        formulaRules,
        addFormulaRule,
        updateFormulaRule,
        deleteFormulaRule,
        attendanceRecords,
        addAttendanceRecord,
        updateAttendanceRecord,
        deleteAttendanceRecord,
        routineItems,
        addRoutineItem,
        deleteRoutineItem,
        templates,
        studentDocuments,
        addStudentDocument,
        deleteStudentDocument,
        genderTerminology,
        updateGenderTerminology,
        searchQuery,
        setSearchQuery,
        filterClass,
        setFilterClass,
        filterGender,
        setFilterGender,
        filterStatus,
        setFilterStatus,
        filterVillage,
        setFilterVillage,
        filterSpecialNeeds,
        setFilterSpecialNeeds,
        filterCategory,
        setFilterCategory,
        resetFilters,
        stats,
        toasts,
        showToast,
        removeToast,
        resetDatabase,
        exportDatabase,
        importDatabase
      }}
    >
      {children}
    </AppContext.Provider>
  );
};

export const useApp = () => {
  const context = useContext(AppContext);
  if (!context) {
    throw new Error('useApp must be used within an AppProvider');
  }
  return context;
};
