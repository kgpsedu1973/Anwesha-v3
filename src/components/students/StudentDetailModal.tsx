import React, { useState } from 'react';
import {
  X,
  User,
  Calendar,
  Phone,
  MapPin,
  Heart,
  FileText,
  Printer,
  Sparkles,
  Edit2,
  IdCard,
  Building,
  CheckCircle2,
  Upload,
  Trash2
} from 'lucide-react';
import { Student } from '../../types';
import { useApp } from '../../context/AppContext';
import { calculateAge, toBanglaDigits, formatBanglaDate } from '../../utils/banglaUtils';
import { FormulaEvaluator } from '../../utils/formulaEvaluator';

interface StudentDetailModalProps {
  student: Student | null;
  isOpen: boolean;
  onClose: () => void;
  onEdit: (student: Student) => void;
  onOpenIdCard: (student: Student) => void;
}

export const StudentDetailModal: React.FC<StudentDetailModalProps> = ({
  student,
  isOpen,
  onClose,
  onEdit,
  onOpenIdCard
}) => {
  const {
    schoolInfo,
    customFields,
    formulaRules,
    studentDocuments,
    addStudentDocument,
    deleteStudentDocument
  } = useApp();

  const [activeTab, setActiveTab] = useState<'profile' | 'documents'>('profile');
  const [docTitle, setDocTitle] = useState('');
  const [docType, setDocType] = useState<any>('BirthCert');
  const [docFile, setDocFile] = useState<string | null>(null);

  if (!isOpen || !student) return null;

  const ageRes = student.birthDate ? calculateAge(student.birthDate) : null;
  const category = FormulaEvaluator.getStudentCategory(student, schoolInfo.internalVillages);

  const studentDocs = studentDocuments.filter(d => d.studentId === student.id);

  const handleAddDocument = (e: React.FormEvent) => {
    e.preventDefault();
    if (!docTitle || !docFile) return;
    addStudentDocument({
      studentId: student.id,
      title: docTitle,
      docType,
      imageUri: docFile
    });
    setDocTitle('');
    setDocFile(null);
  };

  const handlePrint = () => {
    window.print();
  };

  return (
    <div className="fixed inset-0 z-50 overflow-y-auto bg-slate-900/60 backdrop-blur-xs flex items-center justify-center p-4">
      <div className="bg-white rounded-2xl shadow-2xl max-w-2xl w-full overflow-hidden border border-slate-200 animate-in fade-in zoom-in-95 duration-200">
        {/* Modal Header */}
        <div className="bg-gradient-to-r from-emerald-800 to-teal-800 text-white p-6 relative">
          <button
            onClick={onClose}
            className="absolute top-4 right-4 text-white/80 hover:text-white p-1 rounded-lg hover:bg-white/10 transition-colors cursor-pointer"
          >
            <X className="w-5 h-5" />
          </button>

          <div className="flex flex-col sm:flex-row items-center sm:items-start gap-4">
            <div className="w-20 h-20 rounded-2xl bg-white/10 border-2 border-white/30 overflow-hidden flex items-center justify-center shadow-lg shrink-0">
              {student.photoUri ? (
                <img src={student.photoUri} alt={student.name} className="w-full h-full object-cover" />
              ) : (
                <span className="text-3xl">
                  {student.gender === 'ছাত্রী' || student.gender === 'বালিকা' ? '👩' : '👦'}
                </span>
              )}
            </div>

            <div className="text-center sm:text-left space-y-1">
              <div className="flex flex-wrap items-center justify-center sm:justify-start gap-2">
                <h2 className="text-xl font-bold font-serif-bn">{student.name}</h2>
                <span
                  className={`text-xs px-2 py-0.5 rounded-full font-semibold ${
                    category === 'অভ্যন্তরীণ'
                      ? 'bg-emerald-500/30 text-emerald-200 border border-emerald-400/40'
                      : 'bg-amber-500/30 text-amber-200 border border-amber-400/40'
                  }`}
                >
                  {category}
                </span>
              </div>

              <p className="text-xs text-emerald-100/90">
                {student.studentClass} • রোল: {toBanglaDigits(student.rollNumber)} • শাখা: {student.section || 'প্রযোজ্য নয়'}
              </p>
              <p className="text-xs text-emerald-200/80">আইডি: {student.id}</p>
            </div>
          </div>

          {/* Quick Actions Bar in Header */}
          <div className="flex flex-wrap items-center gap-2 mt-4 pt-3 border-t border-white/15">
            <button
              onClick={() => onEdit(student)}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-white/15 hover:bg-white/25 text-xs font-medium transition-colors cursor-pointer"
            >
              <Edit2 className="w-3.5 h-3.5" />
              <span>তথ্য পরিবর্তন</span>
            </button>
            <button
              onClick={() => onOpenIdCard(student)}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-white/15 hover:bg-white/25 text-xs font-medium transition-colors cursor-pointer"
            >
              <IdCard className="w-3.5 h-3.5" />
              <span>আইডি কার্ড</span>
            </button>
            <button
              onClick={handlePrint}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-white/15 hover:bg-white/25 text-xs font-medium transition-colors cursor-pointer"
            >
              <Printer className="w-3.5 h-3.5" />
              <span>প্রোফাইল প্রিন্ট</span>
            </button>
          </div>
        </div>

        {/* Tab Selection */}
        <div className="flex border-b border-slate-200 px-6 pt-2 bg-slate-50">
          <button
            onClick={() => setActiveTab('profile')}
            className={`pb-2 px-3 text-xs font-bold transition-all border-b-2 cursor-pointer ${
              activeTab === 'profile'
                ? 'border-emerald-600 text-emerald-700'
                : 'border-transparent text-slate-500 hover:text-slate-800'
            }`}
          >
            পূর্ণাঙ্গ প্রোফাইল
          </button>
          <button
            onClick={() => setActiveTab('documents')}
            className={`pb-2 px-3 text-xs font-bold transition-all border-b-2 cursor-pointer flex items-center gap-1.5 ${
              activeTab === 'documents'
                ? 'border-emerald-600 text-emerald-700'
                : 'border-transparent text-slate-500 hover:text-slate-800'
            }`}
          >
            <span>সংযুক্ত ডকুমেন্ট</span>
            <span className="text-[10px] px-1.5 py-0.2 rounded-full bg-slate-200 text-slate-700">
              {toBanglaDigits(studentDocs.length)}
            </span>
          </button>
        </div>

        {/* Modal Body */}
        <div className="p-6 max-h-[60vh] overflow-y-auto space-y-5">
          {activeTab === 'profile' ? (
            <>
              {/* Core Details Grid */}
              <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
                <div className="p-3 rounded-xl bg-slate-50 border border-slate-200/80">
                  <span className="text-[11px] text-slate-500 font-semibold block mb-0.5">জন্মতারিখ</span>
                  <span className="font-bold text-xs text-slate-800">
                    {formatBanglaDate(student.birthDate)}
                  </span>
                </div>

                <div className="p-3 rounded-xl bg-emerald-50/60 border border-emerald-200/60">
                  <span className="text-[11px] text-emerald-700 font-semibold block mb-0.5">বর্তমান বয়স</span>
                  <span className="font-bold text-xs text-emerald-900">
                    {ageRes?.formattedText || '—'}
                  </span>
                </div>

                <div className="p-3 rounded-xl bg-slate-50 border border-slate-200/80">
                  <span className="text-[11px] text-slate-500 font-semibold block mb-0.5">লিঙ্গ</span>
                  <span className="font-bold text-xs text-slate-800">{student.gender}</span>
                </div>

                <div className="p-3 rounded-xl bg-slate-50 border border-slate-200/80 sm:col-span-2">
                  <span className="text-[11px] text-slate-500 font-semibold block mb-0.5">জন্ম নিবন্ধন নম্বর</span>
                  <span className="font-bold text-xs font-mono text-slate-800">
                    {student.birthRegNumber || '—'}
                  </span>
                </div>

                <div className="p-3 rounded-xl bg-slate-50 border border-slate-200/80">
                  <span className="text-[11px] text-slate-500 font-semibold block mb-0.5">শিক্ষাবর্ষ</span>
                  <span className="font-bold text-xs text-slate-800">{student.academicYear || '২০২৬'}</span>
                </div>
              </div>

              {/* Family & Contact */}
              <div className="p-4 rounded-xl bg-slate-50 border border-slate-200/80 space-y-2.5">
                <h3 className="text-xs font-bold text-slate-800 uppercase tracking-wider">
                  পারিবারিক ও যোগাযোগের তথ্য
                </h3>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-xs">
                  <div>
                    <span className="text-slate-500 block">পিতার নাম:</span>
                    <span className="font-semibold text-slate-800">{student.fatherName || '—'}</span>
                  </div>
                  <div>
                    <span className="text-slate-500 block">মাতার নাম:</span>
                    <span className="font-semibold text-slate-800">{student.motherName || '—'}</span>
                  </div>
                  <div>
                    <span className="text-slate-500 block">মোবাইল নম্বর:</span>
                    <span className="font-semibold text-slate-800 font-mono">
                      {toBanglaDigits(student.mobile || student.parentContact || '—')}
                    </span>
                  </div>
                  <div>
                    <span className="text-slate-500 block">গ্রাম / এলাকা:</span>
                    <span className="font-semibold text-slate-800">{student.village || '—'}</span>
                  </div>
                </div>
              </div>

              {/* Custom Dynamic Fields */}
              {customFields.length > 0 && (
                <div className="p-4 rounded-xl bg-indigo-50/50 border border-indigo-200/60 space-y-2.5">
                  <h3 className="text-xs font-bold text-indigo-900 uppercase tracking-wider flex items-center gap-1.5">
                    <Sparkles className="w-3.5 h-3.5 text-indigo-600" />
                    <span>কাস্টম তথ্য ও সূত্র ফলাফল</span>
                  </h3>
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-xs">
                    {customFields.map(cf => {
                      const val = FormulaEvaluator.getFieldValue(
                        student,
                        cf.id,
                        customFields,
                        formulaRules
                      );
                      return (
                        <div key={cf.id} className="p-2 rounded-lg bg-white border border-indigo-100">
                          <span className="text-slate-500 block text-[11px]">{cf.name}:</span>
                          <span className="font-bold text-slate-800">{val || '—'}</span>
                        </div>
                      );
                    })}
                  </div>
                </div>
              )}
            </>
          ) : (
            /* Documents Tab */
            <div className="space-y-4">
              {/* Add document box */}
              <form onSubmit={handleAddDocument} className="p-3.5 rounded-xl border border-slate-200 bg-slate-50 space-y-3">
                <span className="text-xs font-bold text-slate-700 block">নতুন ডকুমেন্ট সংযুক্ত করুন</span>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                  <input
                    type="text"
                    required
                    placeholder="ডকুমেন্টের শিরোনাম (যেমন: জন্ম নিবন্ধন সনদ)"
                    value={docTitle}
                    onChange={e => setDocTitle(e.target.value)}
                    className="px-3 py-1.5 text-xs rounded-lg border border-slate-300 bg-white"
                  />
                  <select
                    value={docType}
                    onChange={e => setDocType(e.target.value as any)}
                    className="px-3 py-1.5 text-xs rounded-lg border border-slate-300 bg-white"
                  >
                    <option value="BirthCert">জন্ম নিবন্ধন সনদ</option>
                    <option value="NID">অভিভাবকের NID</option>
                    <option value="Photo">ছবি</option>
                    <option value="ReportCard">নম্বরপত্র</option>
                    <option value="Certificate">সনদপত্র</option>
                    <option value="Other">অন্যান্য</option>
                  </select>
                </div>
                <div className="flex items-center gap-3">
                  <input
                    type="file"
                    accept="image/*"
                    required
                    onChange={e => {
                      const file = e.target.files?.[0];
                      if (file) {
                        const reader = new FileReader();
                        reader.onloadend = () => setDocFile(reader.result as string);
                        reader.readAsDataURL(file);
                      }
                    }}
                    className="text-xs"
                  />
                  <button
                    type="submit"
                    className="px-3 py-1.5 rounded-lg bg-emerald-600 hover:bg-emerald-700 text-white text-xs font-semibold ml-auto cursor-pointer"
                  >
                    আপলোড
                  </button>
                </div>
              </form>

              {/* Document List */}
              {studentDocs.length === 0 ? (
                <div className="py-6 text-center text-slate-400 text-xs">
                  কোন ডকুমেন্ট এখনো সংযুক্ত করা হয়নি
                </div>
              ) : (
                <div className="grid grid-cols-2 gap-3">
                  {studentDocs.map(doc => (
                    <div key={doc.id} className="p-2.5 rounded-xl border border-slate-200 bg-white space-y-2 group">
                      <div className="h-32 rounded-lg bg-slate-100 overflow-hidden flex items-center justify-center">
                        <img src={doc.imageUri} alt={doc.title} className="w-full h-full object-cover" />
                      </div>
                      <div className="flex items-center justify-between">
                        <span className="text-xs font-semibold text-slate-800 truncate" title={doc.title}>
                          {doc.title}
                        </span>
                        <button
                          onClick={() => deleteStudentDocument(doc.id)}
                          className="text-rose-500 hover:text-rose-700 p-1 rounded-md"
                        >
                          <Trash2 className="w-3.5 h-3.5" />
                        </button>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
