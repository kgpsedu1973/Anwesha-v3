export interface Student {
  id: string; // e.g. "STU-2026-001"
  studentClass: string; // e.g. "প্রাক-প্রাথমিক ৪+", "১ম শ্রেণি", "২য় শ্রেণি", etc.
  section: string; // "ক", "খ"
  rollNumber: number;
  name: string; // শিক্ষার্থী নাম
  parentContact: string;
  fatherName: string;
  motherName: string;
  birthDate: string; // YYYY-MM-DD
  mobile: string;
  village: string;
  academicYear: string; // e.g. "২০২৬"
  address: string;
  birthRegNumber: string;
  gender: string; // "ছাত্র" | "ছাত্রী" | "বালক" | "বালিকা" | "ছেলে" | "মেয়ে"
  isSpecialNeeds: boolean;
  status: 'Current' | 'Former' | 'Transferred' | 'Inactive';
  photoUri?: string | null;
  customValues: Record<string, string>; // key-value pairs
  admissionDate: string;
  lastModifiedDate: string;
  createdAt: number;
  updatedAt: number;
}

export interface SchoolInfo {
  id: number;
  schoolName: string;
  address: string;
  eiinCode: string;
  logoUri?: string | null;
  phone: string;
  email: string;
  headTeacherName: string;
  adminName: string;
  adminEmail: string;
  adminPhone: string;
  createdDate: string;
  tagline: string;
  internalVillages: string; // comma separated e.g. "পশ্চিম রামপুর,আমতলী,কৃষ্ণপুর"
  updatedAt: number;
}

export interface CustomField {
  id: string;
  name: string; // Field label in Bangla
  fieldType: 'Text' | 'Number' | 'Date' | 'Phone' | 'Dropdown' | 'Yes/No' | 'Multiple choice' | 'Long text' | 'Calculated';
  options: string[]; // Options if Dropdown
  isCalculated: boolean;
  formulaRuleId?: string | null;
  groupName: string;
  orderIndex: number;
}

export interface FormulaRule {
  id: string;
  ruleName: string;
  targetFieldName: string;
  sourceField: string; // "village", "birthDate", "gender", "studentClass"
  operator: 'EQUALS' | 'NOT_EQUALS' | 'CONTAINS' | 'IN_LIST' | 'GREATER_THAN' | 'LESS_THAN';
  conditionValue: string; // e.g. "পশ্চিম রামপুর,আমতলী,কৃষ্ণপুর"
  resultIfTrue: string;
  resultIfFalse: string;
}

export interface AttendanceRecord {
  id: string;
  date: string; // YYYY-MM-DD
  className: string; // "১ম শ্রেণি" or "ALL"
  presentBoys: number;
  presentGirls: number;
  absentBoys: number;
  absentGirls: number;
  totalBoys: number;
  totalGirls: number;
  notes?: string;
  createdAt: number;
}

export interface RoutineItem {
  id: string;
  routineType: 'Class Routine' | 'Exam Routine';
  className: string;
  subject: string;
  teacher: string;
  day: string; // "রবিবার", "সোমবার", etc.
  startTime: string; // "09:00 AM"
  endTime: string; // "09:45 AM"
  periodName: string; // "১ম পিরিয়ড"
  roomNo?: string;
}

export interface DocumentTemplate {
  id: string;
  title: string;
  contentTemplate: string;
  createdDate: string;
}

export interface StudentDocument {
  id: string;
  studentId: string;
  title: string;
  docType: 'NID' | 'BirthCert' | 'Photo' | 'ReportCard' | 'Certificate' | 'Other';
  imageUri: string;
  notes?: string;
  createdAt: number;
}

export interface RoutineDay {
  id: string;
  date: string;
  day: string;
  subjects: string[];
}

export interface AdmitCardSettings {
  pageSize: 'A4' | 'Letter' | 'Legal';
  orientation: 'portrait' | 'landscape';
  cardsPerPage: number; // 1, 2, 4, 6, 8
  frameStyle: 'solid' | 'dashed' | 'dotted' | 'double' | 'none';
  cardFont: 'serif' | 'sans';
  examName: string;
  signatureBase64?: string;
}

export interface SeatPlanConfig {
  pageSize: 'A4' | 'Letter' | 'Legal';
  orientation: 'portrait' | 'landscape';
  columns: number;
  rows: number;
  examName: string;
  titleText: string;
  showSchoolName: boolean;
  showExamName: boolean;
  showStudentName: boolean;
  showRollNumber: boolean;
  showClass: boolean;
  showRoomNumber: boolean;
  roomNumberText: string;
  showBenchNumber: boolean;
  startBenchNumber: number;
  borderStyle: 'solid' | 'dashed' | 'dotted' | 'double' | 'none';
}

export interface CertificateConfig {
  schoolName: string;
  upazila: string;
  district: string;
  estYear: string;
  govtHeader1: string;
  govtHeader2: string;
  govtHeader3: string;
  certificateTitle: string; // "প্রত্যয়নপত্র" | "প্রশংসাপত্র" | "চারিত্রিক সনদপত্র"
  issueDate: string;
  sessionYear: string;
  serialFormatMode: 'YEAR_CLASS_ROLL' | 'CUSTOM_PREFIX_ROLL' | 'AUTO_INCREMENT' | 'MANUAL';
  customSerialPrefix: string;
  autoIncrementStart: number;
  studyTense: 'PAST' | 'PRESENT'; // "অধ্যয়ন করেছে" | "অধ্যয়ন করছে"
  characterRemark: string;
  wishRemark: string;
  headTeacherTitle: string;
  showHeadTeacherSignature: boolean;
  headTeacherSignatureBase64?: string;
  showCounterfoil: boolean;
  pageSize: 'Legal' | 'A4';
  orientation: 'landscape' | 'portrait';
  borderStyle: 'ornate' | 'double' | 'classic' | 'solid';
}

export type ActiveScreen = 'dashboard' | 'students' | 'tools_hub' | 'custom_fields' | 'settings';
export type ToolRoute = 'doc_scanner' | 'attendance_report' | 'age_calculator' | 'admit_card_maker' | 'seat_plan_maker' | 'certificate_maker';

export interface GenderTerminology {
  boyLabel: string; // "ছাত্র" or "বালক" or "ছেলে"
  girlLabel: string; // "ছাত্রী" or "বালিকা" or "মেয়ে"
}
