import React, { useState } from 'react';
import {
  LayoutGrid,
  ArrowLeft,
  Printer,
  Sparkles,
  Building,
  Users,
  Grid,
  Tag
} from 'lucide-react';
import { useApp } from '../../context/AppContext';
import { toBanglaDigits } from '../../utils/banglaUtils';

export const SeatPlanMakerTool: React.FC<{ onBack: () => void }> = ({ onBack }) => {
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
  const [roomNumber, setRoomNumber] = useState('১০১');
  const [examName, setExamName] = useState('১ম সাময়িক মূল্যায়ন পরীক্ষা ২০২৬');
  const [studentsPerBench, setStudentsPerBench] = useState(2);
  const [mode, setMode] = useState<'stickers' | 'notice'>('stickers');

  const classStudents = students.filter(s => s.studentClass === selectedClass);

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
                সিট প্ল্যান ও বেঞ্চ স্টিকার মেকার
              </h1>
              <span className="text-xs px-2.5 py-0.5 rounded-full bg-indigo-100 text-indigo-800 font-bold">
                পরীক্ষা আসন বিন্যাস
              </span>
            </div>
            <p className="text-xs text-slate-500 mt-0.5">
              কক্ষভিত্তিক আসন তালিকা এবং প্রিন্টযোগ্য ডেস্ক ও বেঞ্চ স্টিকার
            </p>
          </div>
        </div>

        <div className="flex items-center gap-2">
          <button
            onClick={handlePrint}
            className="inline-flex items-center gap-1.5 px-4 py-2 rounded-lg bg-indigo-600 hover:bg-indigo-700 text-white text-xs font-bold shadow-md shadow-indigo-600/20 cursor-pointer"
          >
            <Printer className="w-4 h-4" />
            <span>প্রিন্ট করুন</span>
          </button>
        </div>
      </div>

      {/* Control Configuration Bar */}
      <div className="bg-white p-5 rounded-2xl border border-slate-200/80 shadow-xs space-y-4 no-print">
        <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 gap-3">
          <div>
            <label className="block text-xs font-semibold text-slate-700 mb-1">শ্রেণি</label>
            <select
              value={selectedClass}
              onChange={e => setSelectedClass(e.target.value)}
              className="w-full px-3 py-2 text-xs rounded-lg border border-slate-300 bg-slate-50 font-semibold"
            >
              {standardClasses.map(c => (
                <option key={c} value={c}>
                  {c}
                </option>
              ))}
            </select>
          </div>

          <div>
            <label className="block text-xs font-semibold text-slate-700 mb-1">পরীক্ষার নাম</label>
            <input
              type="text"
              value={examName}
              onChange={e => setExamName(e.target.value)}
              className="w-full px-3 py-2 text-xs rounded-lg border border-slate-300 bg-slate-50"
            />
          </div>

          <div>
            <label className="block text-xs font-semibold text-slate-700 mb-1">কক্ষ নম্বর</label>
            <input
              type="text"
              value={roomNumber}
              onChange={e => setRoomNumber(e.target.value)}
              className="w-full px-3 py-2 text-xs rounded-lg border border-slate-300 bg-slate-50 font-bold"
            />
          </div>

          <div>
            <label className="block text-xs font-semibold text-slate-700 mb-1">প্রদর্শন ধরন</label>
            <div className="flex gap-1 bg-slate-100 p-1 rounded-lg">
              <button
                onClick={() => setMode('stickers')}
                className={`flex-1 py-1 text-xs font-semibold rounded-md transition-all cursor-pointer ${
                  mode === 'stickers' ? 'bg-white text-indigo-700 shadow-xs' : 'text-slate-600'
                }`}
              >
                বেঞ্চ স্টিকার
              </button>
              <button
                onClick={() => setMode('notice')}
                className={`flex-1 py-1 text-xs font-semibold rounded-md transition-all cursor-pointer ${
                  mode === 'notice' ? 'bg-white text-indigo-700 shadow-xs' : 'text-slate-600'
                }`}
              >
                নোটিশ শিট
              </button>
            </div>
          </div>
        </div>
      </div>

      {/* MODE 1: PRINTABLE BENCH STICKERS */}
      {mode === 'stickers' ? (
        <div className="space-y-4">
          <div className="flex items-center justify-between no-print">
            <h3 className="font-bold text-sm text-slate-800 font-serif-bn">
              বেঞ্চ স্টিকার শিট ({toBanglaDigits(classStudents.length)} জন শিক্ষার্থী)
            </h3>
          </div>

          <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-4 print:grid-cols-3">
            {classStudents.map((student, idx) => (
              <div
                key={student.id}
                className="bg-white rounded-xl border-2 border-indigo-950 p-3 shadow-xs space-y-2 text-center break-inside-avoid relative overflow-hidden"
              >
                {/* School Name mini header */}
                <div className="border-b border-indigo-900 pb-1">
                  <div className="text-[10px] font-bold text-slate-900 font-serif-bn truncate">
                    {schoolInfo.schoolName}
                  </div>
                  <div className="text-[8px] text-slate-500">{examName}</div>
                </div>

                {/* Big Roll Number */}
                <div className="py-1">
                  <div className="text-[10px] text-slate-500 uppercase font-semibold">রোল নম্বর</div>
                  <div className="text-3xl font-black text-indigo-950 font-serif-bn">
                    {toBanglaDigits(student.rollNumber)}
                  </div>
                </div>

                {/* Name & Class Info */}
                <div className="bg-indigo-50/70 rounded-lg p-1.5 text-slate-800 space-y-0.5">
                  <div className="font-bold text-xs truncate">{student.name}</div>
                  <div className="text-[10px] text-slate-600">
                    {student.studentClass} • কক্ষ: {roomNumber}
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>
      ) : (
        /* MODE 2: MASTER NOTICE BOARD SHEET */
        <div className="bg-white p-8 rounded-2xl border border-slate-200/80 shadow-xs space-y-6">
          <div className="text-center space-y-1 border-b border-slate-200 pb-4">
            <h2 className="text-lg font-bold font-serif-bn text-slate-900">{schoolInfo.schoolName}</h2>
            <p className="text-xs text-slate-600">{schoolInfo.address}</p>
            <h3 className="text-sm font-bold text-indigo-900 pt-1">
              কক্ষভিত্তিক আসন বণ্টন তালিকা — {examName}
            </h3>
          </div>

          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs border border-slate-300">
              <thead className="bg-slate-100 text-slate-700 font-bold border-b border-slate-300 text-center">
                <tr>
                  <th className="p-2.5 border border-slate-300">কক্ষ নং</th>
                  <th className="p-2.5 border border-slate-300 text-left">শ্রেণি</th>
                  <th className="p-2.5 border border-slate-300">রোল পরিসর (From - To)</th>
                  <th className="p-2.5 border border-slate-300">মোট পরীক্ষার্থী</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-200 text-center">
                <tr>
                  <td className="p-3 border border-slate-300 font-bold text-indigo-900 text-sm">
                    {roomNumber}
                  </td>
                  <td className="p-3 border border-slate-300 font-bold text-slate-800 text-left">
                    {selectedClass}
                  </td>
                  <td className="p-3 border border-slate-300 font-semibold text-slate-700">
                    ১ হতে {toBanglaDigits(classStudents.length)} পর্যন্ত
                  </td>
                  <td className="p-3 border border-slate-300 font-bold text-emerald-800">
                    {toBanglaDigits(classStudents.length)} জন
                  </td>
                </tr>
              </tbody>
            </table>
          </div>

          <div className="pt-12 flex justify-between items-end text-xs text-slate-700 px-6">
            <div className="text-center space-y-1">
              <div className="w-36 border-t border-slate-400" />
              <span className="font-semibold block">হল সুপার / পরীক্ষা নিয়ন্ত্রক</span>
            </div>
            <div className="text-center space-y-1">
              <div className="w-36 border-t border-slate-400" />
              <span className="font-bold block">প্রধান শিক্ষক</span>
              <span className="text-[11px] text-slate-500">{schoolInfo.schoolName}</span>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
