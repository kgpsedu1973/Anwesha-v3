import React, { useState } from 'react';
import {
  FileText,
  ArrowLeft,
  Printer,
  Plus,
  Trash2,
  Calendar,
  Clock,
  BookOpen,
  GraduationCap,
  Sparkles,
  Check
} from 'lucide-react';
import { useApp } from '../../context/AppContext';
import { toBanglaDigits, formatBanglaDate } from '../../utils/banglaUtils';
import { Student } from '../../types';

interface ExamSubject {
  id: string;
  date: string;
  time: string;
  subjectName: string;
}

export const AdmitCardMakerTool: React.FC<{ onBack: () => void }> = ({ onBack }) => {
  const { schoolInfo, students } = useApp();

  const standardClasses = [
    'প্রাক-প্রাথমিক ৪+',
    'প্রাক-প্রাথমিক ৫+',
    '১ম শ্রেণি',
    '২য় শ্রেণি',
    '৩য় শ্রেণি',
    '৪র্থ শ্রেণি',
    '৫ম শ্রেণি'
  ];

  const [selectedClass, setSelectedClass] = useState('১ম শ্রেণি');
  const [examTitle, setExamTitle] = useState('১ম সাময়িক মূল্যায়ন পরীক্ষা ২০২৬');
  const [academicYear, setAcademicYear] = useState('২০২৬');
  const [instructions, setInstructions] = useState(
    '১. পরীক্ষা শুরুর ১৫ মিনিট পূর্বে আসন গ্রহণ করতে হবে।\n২. প্রবেশপত্র ছাড়া পরীক্ষা কক্ষে প্রবেশ নিষেধ।\n৩. মোবাইল ফোন বা কোনো ডিজিটাল ডিভাইস আনা সম্পূর্ণ নিষিদ্ধ।'
  );

  const [routine, setRoutine] = useState<ExamSubject[]>([
    { id: '1', date: '2026-04-15', time: 'সকাল ১০:০০ - ১২:০০', subjectName: 'বাংলা' },
    { id: '2', date: '2026-04-16', time: 'সকাল ১০:০০ - ১২:০০', subjectName: 'ইংরেজি' },
    { id: '3', date: '2026-04-17', time: 'সকাল ১০:০০ - ১২:০০', subjectName: 'গণিত' },
    { id: '4', date: '2026-04-18', time: 'সকাল ১০:০০ - ১২:০০', subjectName: 'প্রাথমিক বিজ্ঞান' }
  ]);

  const [newSubDate, setNewSubDate] = useState('2026-04-19');
  const [newSubTime, setNewSubTime] = useState('সকাল ১০:০০ - ১২:০০');
  const [newSubName, setNewSubName] = useState('');

  const classStudents = students.filter(s => s.studentClass === selectedClass);

  const handleAddSubject = () => {
    if (!newSubName.trim()) return;
    setRoutine(prev => [
      ...prev,
      {
        id: Date.now().toString(),
        date: newSubDate,
        time: newSubTime,
        subjectName: newSubName
      }
    ]);
    setNewSubName('');
  };

  const handleRemoveSubject = (id: string) => {
    setRoutine(prev => prev.filter(item => item.id !== id));
  };

  const handlePrint = () => {
    window.print();
  };

  return (
    <div className="space-y-6 pb-16">
      {/* Top Header */}
      <div className="bg-white p-5 rounded-2xl border border-slate-200/80 shadow-xs flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div className="flex items-center gap-3">
          <button
            onClick={onBack}
            className="p-2 rounded-xl border border-slate-200 hover:bg-slate-50 text-slate-600 transition-colors cursor-pointer"
          >
            <ArrowLeft className="w-5 h-5" />
          </button>
          <div>
            <div className="flex items-center gap-2">
              <h1 className="text-xl font-bold text-slate-900 font-serif-bn">
                প্রবেশপত্র (Admit Card) মেকার
              </h1>
              <span className="text-xs px-2.5 py-0.5 rounded-full bg-emerald-100 text-emerald-800 font-bold">
                পরীক্ষা প্রস্তুতি
              </span>
            </div>
            <p className="text-xs text-slate-500 mt-0.5">
              পরীক্ষার রুটিনসহ স্বয়ংক্রিয় ব্যাচ এডমিট কার্ড ও সিট প্ল্যান জেনারেটর
            </p>
          </div>
        </div>

        <button
          onClick={handlePrint}
          className="inline-flex items-center gap-1.5 px-4 py-2 rounded-lg bg-emerald-600 hover:bg-emerald-700 text-white text-xs font-bold shadow-md shadow-emerald-600/20 cursor-pointer"
        >
          <Printer className="w-4 h-4" />
          <span>সকল এডমিট কার্ড প্রিন্ট করুন</span>
        </button>
      </div>

      {/* Routine & Exam Settings */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 no-print">
        {/* Settings Column */}
        <div className="bg-white p-5 rounded-2xl border border-slate-200/80 shadow-xs space-y-4">
          <h3 className="font-bold text-sm text-slate-800 border-b border-slate-100 pb-2">
            পরীক্ষার তথ্য ও শ্রেণি
          </h3>

          <div>
            <label className="block text-xs font-semibold text-slate-700 mb-1">শ্রেণি নির্বাচন</label>
            <select
              value={selectedClass}
              onChange={e => setSelectedClass(e.target.value)}
              className="w-full px-3 py-2 text-xs rounded-lg border border-slate-300 bg-slate-50 font-semibold text-slate-800"
            >
              {standardClasses.map(c => (
                <option key={c} value={c}>
                  {c} ({toBanglaDigits(students.filter(s => s.studentClass === c).length)} জন শিক্ষার্থী)
                </option>
              ))}
            </select>
          </div>

          <div>
            <label className="block text-xs font-semibold text-slate-700 mb-1">পরীক্ষার নাম</label>
            <input
              type="text"
              value={examTitle}
              onChange={e => setExamTitle(e.target.value)}
              className="w-full px-3 py-2 text-xs rounded-lg border border-slate-300 bg-slate-50 font-medium text-slate-800"
            />
          </div>

          <div>
            <label className="block text-xs font-semibold text-slate-700 mb-1">শিক্ষাবর্ষ</label>
            <input
              type="text"
              value={academicYear}
              onChange={e => setAcademicYear(e.target.value)}
              className="w-full px-3 py-2 text-xs rounded-lg border border-slate-300 bg-slate-50 font-medium text-slate-800"
            />
          </div>

          <div>
            <label className="block text-xs font-semibold text-slate-700 mb-1">পরীক্ষার্থীদের নির্দেশনাবলী</label>
            <textarea
              rows={4}
              value={instructions}
              onChange={e => setInstructions(e.target.value)}
              className="w-full px-3 py-2 text-xs rounded-lg border border-slate-300 bg-slate-50 text-slate-700 leading-relaxed"
            />
          </div>
        </div>

        {/* Routine Builder Column */}
        <div className="bg-white p-5 rounded-2xl border border-slate-200/80 shadow-xs space-y-4 lg:col-span-2">
          <h3 className="font-bold text-sm text-slate-800 border-b border-slate-100 pb-2">
            পরীক্ষার সময়সূচি (রুটিন)
          </h3>

          {/* Add Subject Row */}
          <div className="p-3 rounded-xl bg-slate-50 border border-slate-200/80 grid grid-cols-1 sm:grid-cols-4 gap-2 text-xs">
            <input
              type="text"
              placeholder="বিষয়ের নাম (যেমন: গণিত)"
              value={newSubName}
              onChange={e => setNewSubName(e.target.value)}
              className="px-2.5 py-1.5 rounded-lg border border-slate-300 bg-white"
            />
            <input
              type="date"
              value={newSubDate}
              onChange={e => setNewSubDate(e.target.value)}
              className="px-2.5 py-1.5 rounded-lg border border-slate-300 bg-white"
            />
            <input
              type="text"
              placeholder="সময় (যেমন: সকাল ১০:০০)"
              value={newSubTime}
              onChange={e => setNewSubTime(e.target.value)}
              className="px-2.5 py-1.5 rounded-lg border border-slate-300 bg-white"
            />
            <button
              onClick={handleAddSubject}
              className="inline-flex items-center justify-center gap-1 px-3 py-1.5 rounded-lg bg-emerald-600 hover:bg-emerald-700 text-white font-semibold cursor-pointer"
            >
              <Plus className="w-4 h-4" />
              <span>বিষয় যোগ</span>
            </button>
          </div>

          {/* Routine Table */}
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="bg-slate-100 text-slate-600 font-bold border-b border-slate-200">
                <tr>
                  <th className="p-2">বিষয়</th>
                  <th className="p-2">তারিখ</th>
                  <th className="p-2">সময়</th>
                  <th className="p-2 text-right">মুছুন</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {routine.map(sub => (
                  <tr key={sub.id}>
                    <td className="p-2 font-bold text-slate-800">{sub.subjectName}</td>
                    <td className="p-2 text-slate-600">{formatBanglaDate(sub.date)}</td>
                    <td className="p-2 text-slate-600">{sub.time}</td>
                    <td className="p-2 text-right">
                      <button
                        onClick={() => handleRemoveSubject(sub.id)}
                        className="text-rose-500 hover:text-rose-700 p-1"
                      >
                        <Trash2 className="w-3.5 h-3.5" />
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </div>

      {/* Printable Admit Cards Grid */}
      <div className="space-y-4">
        <div className="flex items-center justify-between no-print">
          <h3 className="font-bold text-base text-slate-800 font-serif-bn">
            প্রবেশপত্র প্রিভিউ ({selectedClass} — মোট {toBanglaDigits(classStudents.length)} জন)
          </h3>
        </div>

        {classStudents.length === 0 ? (
          <div className="bg-white p-8 rounded-2xl border border-slate-200 text-center text-slate-400 text-xs">
            এই শ্রেণিতে কোনো শিক্ষার্থী পাওয়া যায়নি
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6 print:grid-cols-2">
            {classStudents.map(student => (
              <div
                key={student.id}
                className="bg-white rounded-2xl border-2 border-slate-800 p-5 shadow-sm space-y-3 print:border-slate-800 print:shadow-none break-inside-avoid"
              >
                {/* Header */}
                <div className="text-center border-b-2 border-slate-800 pb-2 space-y-0.5">
                  <h4 className="font-bold text-sm font-serif-bn text-slate-900 leading-tight">
                    {schoolInfo.schoolName}
                  </h4>
                  <p className="text-[10px] text-slate-600">{schoolInfo.address}</p>
                  <div className="inline-block px-3 py-0.5 rounded-full bg-slate-900 text-white text-[11px] font-bold mt-1">
                    প্রবেশপত্র — {examTitle}
                  </div>
                </div>

                {/* Student Info & Photo */}
                <div className="flex items-start gap-4 pt-1 text-xs">
                  <div className="w-16 h-20 rounded-lg border border-slate-400 bg-slate-50 flex items-center justify-center overflow-hidden shrink-0">
                    {student.photoUri ? (
                      <img src={student.photoUri} alt="" className="w-full h-full object-cover" />
                    ) : (
                      <span className="text-2xl">
                        {student.gender === 'ছাত্রী' || student.gender === 'বালিকা' ? '👩' : '👦'}
                      </span>
                    )}
                  </div>

                  <div className="space-y-1 flex-1 min-w-0">
                    <div className="font-bold text-slate-900 text-sm">{student.name}</div>
                    <div className="grid grid-cols-2 gap-x-2 text-[11px] text-slate-700">
                      <div>শ্রেণি: <strong>{student.studentClass}</strong></div>
                      <div>রোল: <strong>{toBanglaDigits(student.rollNumber)}</strong></div>
                      <div>শাখা: <strong>{student.section || '—'}</strong></div>
                      <div>আইডি: <strong className="font-mono">{student.id}</strong></div>
                      <div className="col-span-2 truncate">পিতা: {student.fatherName || '—'}</div>
                    </div>
                  </div>
                </div>

                {/* Exam Routine Table */}
                {routine.length > 0 && (
                  <div className="border border-slate-300 rounded-lg overflow-hidden">
                    <table className="w-full text-left text-[10px]">
                      <thead className="bg-slate-100 font-bold border-b border-slate-300">
                        <tr>
                          <th className="p-1">বিষয়</th>
                          <th className="p-1">তারিখ</th>
                          <th className="p-1">সময়</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-slate-200">
                        {routine.map(r => (
                          <tr key={r.id}>
                            <td className="p-1 font-semibold">{r.subjectName}</td>
                            <td className="p-1">{formatBanglaDate(r.date)}</td>
                            <td className="p-1">{r.time}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}

                {/* Instructions */}
                <div className="text-[9px] text-slate-600 whitespace-pre-line bg-slate-50 p-2 rounded-md border border-slate-200 leading-tight">
                  {instructions}
                </div>

                {/* Signatures */}
                <div className="flex justify-between items-end pt-4 text-[10px] text-slate-700">
                  <div className="text-center">
                    <div className="w-24 border-t border-slate-500" />
                    <span>শিক্ষার্থীর স্বাক্ষর</span>
                  </div>
                  <div className="text-center">
                    <div className="w-24 border-t border-slate-500" />
                    <span className="font-bold">প্রধান শিক্ষক</span>
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
};
