import React, { useState, useMemo } from 'react';
import {
  Award,
  ArrowLeft,
  Printer,
  Sparkles,
  User,
  Calendar,
  CheckCircle2,
  FileCheck
} from 'lucide-react';
import { useApp } from '../../context/AppContext';
import {
  toBanglaDigits,
  formatBanglaDate,
  getTodayDateStr
} from '../../utils/banglaUtils';

export const CertificateMakerTool: React.FC<{ onBack: () => void }> = ({ onBack }) => {
  const { schoolInfo, students } = useApp();

  const [selectedStudentId, setSelectedStudentId] = useState(students[0]?.id || '');
  const [certType, setCertType] = useState<'testimonial' | 'character' | 'transfer' | 'birthDate'>('testimonial');
  const [issueDate, setIssueDate] = useState(getTodayDateStr());
  const [memoNo, setMemoNo] = useState('পাপ্রাবি/২০২৬/০৮');
  const [customRemark, setCustomRemark] = useState('সে একজন শান্ত, বিনয়ী ও নিয়মিত শিক্ষার্থী। তাহার নৈতিক চরিত্র উত্তম।');

  const selectedStudent = students.find(s => s.id === selectedStudentId) || students[0];

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
                প্রত্যয়নপত্র ও সনদ মেকার
              </h1>
              <span className="text-xs px-2.5 py-0.5 rounded-full bg-amber-100 text-amber-800 font-bold">
                সনদ জেনারেটর
              </span>
            </div>
            <p className="text-xs text-slate-500 mt-0.5">
              অফিসিয়াল প্রত্যয়নপত্র, চারিত্রিক প্রশংসাপত্র ও জন্ম সনদ প্রস্তুতকরণ
            </p>
          </div>
        </div>

        <button
          onClick={handlePrint}
          className="inline-flex items-center gap-1.5 px-4 py-2 rounded-lg bg-amber-600 hover:bg-amber-700 text-white text-xs font-bold shadow-md shadow-amber-600/20 cursor-pointer"
        >
          <Printer className="w-4 h-4" />
          <span>সনদপত্র প্রিন্ট করুন</span>
        </button>
      </div>

      {/* Control Panel */}
      <div className="bg-white p-5 rounded-2xl border border-slate-200/80 shadow-xs space-y-4 no-print">
        <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 gap-3">
          <div>
            <label className="block text-xs font-semibold text-slate-700 mb-1">শিক্ষার্থী নির্বাচন</label>
            <select
              value={selectedStudentId}
              onChange={e => setSelectedStudentId(e.target.value)}
              className="w-full px-3 py-2 text-xs rounded-lg border border-slate-300 bg-slate-50 font-semibold"
            >
              {students.map(s => (
                <option key={s.id} value={s.id}>
                  {s.name} ({s.studentClass}, রোল {toBanglaDigits(s.rollNumber)})
                </option>
              ))}
            </select>
          </div>

          <div>
            <label className="block text-xs font-semibold text-slate-700 mb-1">সনদের ধরন</label>
            <select
              value={certType}
              onChange={e => setCertType(e.target.value as any)}
              className="w-full px-3 py-2 text-xs rounded-lg border border-slate-300 bg-slate-50 font-medium"
            >
              <option value="testimonial">অধ্যয়নরত প্রত্যয়নপত্র (Testimonial)</option>
              <option value="character">চারিত্রিক প্রশংসাপত্র</option>
              <option value="birthDate">বয়স ও জন্মতারিখ প্রত্যয়ন</option>
              <option value="transfer">ছাড়পত্র (Transfer Certificate)</option>
            </select>
          </div>

          <div>
            <label className="block text-xs font-semibold text-slate-700 mb-1">স্মারক নম্বর</label>
            <input
              type="text"
              value={memoNo}
              onChange={e => setMemoNo(e.target.value)}
              className="w-full px-3 py-2 text-xs rounded-lg border border-slate-300 bg-slate-50 font-mono text-xs"
            />
          </div>

          <div>
            <label className="block text-xs font-semibold text-slate-700 mb-1">ইস্যুর তারিখ</label>
            <input
              type="date"
              value={issueDate}
              onChange={e => setIssueDate(e.target.value)}
              className="w-full px-3 py-2 text-xs rounded-lg border border-slate-300 bg-slate-50"
            />
          </div>
        </div>

        <div>
          <label className="block text-xs font-semibold text-slate-700 mb-1">বিশেষ মন্তব্য / অতিরিক্ত বাক্য</label>
          <input
            type="text"
            value={customRemark}
            onChange={e => setCustomRemark(e.target.value)}
            className="w-full px-3 py-2 text-xs rounded-lg border border-slate-300 bg-slate-50"
          />
        </div>
      </div>

      {/* Certificate Live Canvas */}
      {selectedStudent && (
        <div className="max-w-3xl mx-auto">
          <div className="bg-white rounded-2xl border-4 border-double border-amber-900 p-8 sm:p-12 shadow-xl relative text-slate-800 space-y-6 font-serif-bn">
            {/* Top School Header */}
            <div className="text-center space-y-1 border-b-2 border-amber-900/40 pb-4">
              <span className="text-[11px] font-sans font-bold text-amber-800 uppercase tracking-widest block">
                গণপ্রজাতন্ত্রী বাংলাদেশ সরকার
              </span>
              <h2 className="text-2xl sm:text-3xl font-bold text-slate-900">
                {schoolInfo.schoolName}
              </h2>
              <p className="text-xs text-slate-600 font-sans">
                {schoolInfo.address} • স্থাপিত: ১৯৫৩ খ্রি.
              </p>
            </div>

            {/* Memo & Date */}
            <div className="flex justify-between items-center text-xs font-sans border-b border-slate-200 pb-2">
              <div>স্মারক নং: <strong className="font-mono">{memoNo}</strong></div>
              <div>তারিখ: <strong>{formatBanglaDate(issueDate)}</strong></div>
            </div>

            {/* Certificate Title Badge */}
            <div className="text-center py-2">
              <span className="inline-block px-6 py-1.5 rounded-full border-2 border-amber-800 bg-amber-50 text-amber-950 font-bold text-base shadow-xs">
                {certType === 'testimonial' && 'অধ্যয়নরত প্রত্যয়নপত্র'}
                {certType === 'character' && 'চারিত্রিক প্রশংসাপত্র'}
                {certType === 'birthDate' && 'বয়স ও জন্মতারিখ প্রত্যয়নপত্র'}
                {certType === 'transfer' && 'ছাড়পত্র (Transfer Certificate)'}
              </span>
            </div>

            {/* Main Certificate Text Body */}
            <div className="text-sm leading-loose text-justify text-slate-800 indent-8 space-y-3 font-medium">
              <p>
                এই মর্মে প্রত্যয়ন করা যাইতেছে যে, <strong>{selectedStudent.name}</strong>, পিতা: <strong>{selectedStudent.fatherName || '—'}</strong>, মাতা: <strong>{selectedStudent.motherName || '—'}</strong>, গ্রাম: <strong>{selectedStudent.village || '—'}</strong>, এই বিদ্যালয়ের <strong>{selectedStudent.studentClass}</strong>-এর একজন নিয়মিত শিক্ষার্থী। তাহার শ্রেণি রোল নম্বর <strong>{toBanglaDigits(selectedStudent.rollNumber)}</strong> এবং বিদ্যালয় ভর্তি রেজিস্টার অনুযায়ী জন্ম তারিখ <strong>{formatBanglaDate(selectedStudent.birthDate)}</strong> (জন্ম নিবন্ধন নম্বর: <span className="font-mono text-xs">{selectedStudent.birthRegNumber || '—'}</span>)।
              </p>
              <p>
                আমার জানা মতে সে কোনো প্রকার রাষ্ট্র বা শৃঙ্খলা পরিপন্থী কাজে জড়িত নহে। {customRemark}
              </p>
              <p>
                আমি তাহার সর্বাঙ্গীন মঙ্গল ও ভবিষ্যৎ জীবনের উজ্জ্বল সাফল্য কামনা করি।
              </p>
            </div>

            {/* Signatures */}
            <div className="pt-16 flex justify-between items-end text-xs text-slate-800 font-sans px-4">
              <div className="text-center space-y-1">
                <div className="w-36 border-t border-slate-500" />
                <span className="font-semibold block">তুলনাকারী / সহকারী শিক্ষক</span>
              </div>
              <div className="text-center space-y-1">
                <div className="w-40 border-t border-slate-800" />
                <span className="font-bold text-sm block">প্রধান শিক্ষক</span>
                <span className="text-[10px] text-slate-600">{schoolInfo.schoolName}</span>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
