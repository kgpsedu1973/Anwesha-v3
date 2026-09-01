import React, { useRef } from 'react';
import { X, Printer, Download, GraduationCap, QrCode } from 'lucide-react';
import { Student } from '../../types';
import { useApp } from '../../context/AppContext';
import { toBanglaDigits, formatBanglaDate } from '../../utils/banglaUtils';

interface StudentIdCardModalProps {
  student: Student | null;
  isOpen: boolean;
  onClose: () => void;
}

export const StudentIdCardModal: React.FC<StudentIdCardModalProps> = ({
  student,
  isOpen,
  onClose
}) => {
  const { schoolInfo } = useApp();
  const cardRef = useRef<HTMLDivElement>(null);

  if (!isOpen || !student) return null;

  const handlePrint = () => {
    window.print();
  };

  return (
    <div className="fixed inset-0 z-50 overflow-y-auto bg-slate-900/60 backdrop-blur-xs flex items-center justify-center p-4">
      <div className="bg-white rounded-2xl shadow-2xl max-w-lg w-full overflow-hidden border border-slate-200 animate-in fade-in zoom-in-95 duration-200">
        {/* Header */}
        <div className="bg-slate-800 text-white px-5 py-3 flex items-center justify-between">
          <h3 className="font-bold text-sm">শিক্ষার্থী আইডি কার্ড প্রিভিউ</h3>
          <div className="flex items-center gap-2">
            <button
              onClick={handlePrint}
              className="inline-flex items-center gap-1.5 px-3 py-1 rounded-lg bg-emerald-600 hover:bg-emerald-700 text-xs font-semibold transition-colors cursor-pointer"
            >
              <Printer className="w-3.5 h-3.5" />
              <span>প্রিন্ট করুন</span>
            </button>
            <button
              onClick={onClose}
              className="text-slate-400 hover:text-white p-1 rounded-lg hover:bg-slate-700"
            >
              <X className="w-4 h-4" />
            </button>
          </div>
        </div>

        {/* Card Canvas */}
        <div className="p-8 bg-slate-100 flex items-center justify-center">
          {/* Authentic High-Resolution ID Card */}
          <div
            ref={cardRef}
            className="w-72 bg-white rounded-2xl overflow-hidden shadow-xl border-2 border-emerald-700 relative text-slate-800 font-sans"
          >
            {/* Top School Header Bar */}
            <div className="bg-gradient-to-r from-emerald-800 via-teal-800 to-emerald-900 text-white p-3 text-center space-y-0.5 relative overflow-hidden">
              <div className="flex items-center justify-center gap-1.5 mb-0.5">
                <GraduationCap className="w-4 h-4 text-emerald-300" />
                <span className="text-[10px] font-bold tracking-wider uppercase text-emerald-200">
                  শিক্ষার্থী পরিচয়পত্র
                </span>
              </div>
              <h4 className="font-bold text-xs font-serif-bn leading-tight">
                {schoolInfo.schoolName}
              </h4>
              <p className="text-[9px] text-emerald-200/90 truncate">{schoolInfo.address}</p>
            </div>

            {/* Photo & Identity Core */}
            <div className="p-4 flex flex-col items-center text-center space-y-2.5">
              {/* Photo Box */}
              <div className="w-20 h-24 rounded-xl border-2 border-emerald-600 bg-slate-50 overflow-hidden shadow-xs flex items-center justify-center">
                {student.photoUri ? (
                  <img
                    src={student.photoUri}
                    alt={student.name}
                    className="w-full h-full object-cover"
                  />
                ) : (
                  <span className="text-3xl">
                    {student.gender === 'ছাত্রী' || student.gender === 'বালিকা' ? '👩' : '👦'}
                  </span>
                )}
              </div>

              {/* Student Name */}
              <div>
                <h3 className="font-bold text-sm text-slate-900 font-serif-bn">{student.name}</h3>
                <span className="inline-block px-2.5 py-0.5 mt-0.5 rounded-full bg-emerald-100 text-emerald-800 text-[11px] font-bold">
                  {student.studentClass} • রোল: {toBanglaDigits(student.rollNumber)}
                </span>
              </div>

              {/* Details Key-Value */}
              <div className="w-full bg-slate-50 rounded-xl p-2.5 border border-slate-200/80 text-[11px] space-y-1 text-left">
                <div className="flex justify-between">
                  <span className="text-slate-500">আইডি নং:</span>
                  <span className="font-bold font-mono text-slate-800">{student.id}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-slate-500">পিতা:</span>
                  <span className="font-semibold text-slate-800">{student.fatherName || '—'}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-slate-500">জন্মতারিখ:</span>
                  <span className="font-semibold text-slate-800">
                    {formatBanglaDate(student.birthDate)}
                  </span>
                </div>
                <div className="flex justify-between">
                  <span className="text-slate-500">মোবাইল:</span>
                  <span className="font-semibold font-mono text-slate-800">
                    {toBanglaDigits(student.mobile || student.parentContact || '—')}
                  </span>
                </div>
                <div className="flex justify-between">
                  <span className="text-slate-500">গ্রাম:</span>
                  <span className="font-semibold text-slate-800">{student.village || '—'}</span>
                </div>
              </div>

              {/* Card Footer with Signatures & Barcode */}
              <div className="w-full pt-2 flex items-end justify-between border-t border-slate-200 text-[9px]">
                <div className="text-left space-y-1">
                  <div className="w-10 h-10 border border-slate-300 rounded-md flex items-center justify-center p-1 bg-white">
                    <QrCode className="w-8 h-8 text-slate-800" />
                  </div>
                  <span className="text-[8px] text-slate-400 block">স্ক্যান করে যাচাই</span>
                </div>

                <div className="text-right space-y-0.5">
                  <div className="font-signature text-xs text-slate-600 font-serif italic mb-0.5">
                    রফিকুল ইসলাম
                  </div>
                  <div className="w-20 border-t border-slate-400" />
                  <span className="font-bold text-slate-700 block">প্রধান শিক্ষক</span>
                </div>
              </div>
            </div>

            {/* Bottom Color Accent */}
            <div className="h-1.5 bg-gradient-to-r from-emerald-600 to-teal-500" />
          </div>
        </div>
      </div>
    </div>
  );
};
