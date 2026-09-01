import React, { useState } from 'react';
import { X, Upload, Download, FileSpreadsheet, Check, AlertCircle } from 'lucide-react';
import { useApp } from '../../context/AppContext';
import { Student } from '../../types';
import { getTodayDateStr } from '../../utils/banglaUtils';

interface StudentImportExportModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export const StudentImportExportModal: React.FC<StudentImportExportModalProps> = ({
  isOpen,
  onClose
}) => {
  const { students, importStudents, showToast } = useApp();
  const [csvContent, setCsvContent] = useState('');
  const [importPreview, setImportPreview] = useState<Student[]>([]);

  if (!isOpen) return null;

  const handleDownloadTemplate = () => {
    const headers = 'name,studentClass,rollNumber,gender,birthDate,mobile,village,fatherName,motherName,birthRegNumber\n';
    const sample = 'তানভীর আহমেদ,১ম শ্রেণি,1,ছাত্র,2019-03-12,01712345678,পশ্চিম রামপুর,মো: কামাল হোসেন,রেহানা পারভীন,20191912834000101\nরাইসা খাতুন,১ম শ্রেণি,2,ছাত্রী,2019-07-25,01819876543,আমতলী,জহিরুল ইসলাম,শাহিনুর বেগম,20191912834000102';
    const blob = new Blob([headers + sample], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'anwesha_student_template.csv';
    a.click();
    URL.revokeObjectURL(url);
  };

  const handleExportCSV = () => {
    const headers = 'ID,Name,Class,Section,Roll,Gender,BirthDate,Mobile,Village,FatherName,MotherName,BirthRegNumber,Status\n';
    const rows = students.map(s =>
      `"${s.id}","${s.name}","${s.studentClass}","${s.section}","${s.rollNumber}","${s.gender}","${s.birthDate}","${s.mobile}","${s.village}","${s.fatherName}","${s.motherName}","${s.birthRegNumber}","${s.status}"`
    ).join('\n');

    const blob = new Blob([headers + rows], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `students_export_${getTodayDateStr()}.csv`;
    a.click();
    URL.revokeObjectURL(url);
    showToast('শিক্ষার্থী তালিকা CSV ফাইল হিসেবে ডাউনলোড হয়েছে');
  };

  const handleFileUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    const reader = new FileReader();
    reader.onload = event => {
      const text = event.target?.result as string;
      setCsvContent(text);
      parseCSV(text);
    };
    reader.readAsText(file);
  };

  const parseCSV = (text: string) => {
    try {
      const lines = text.split('\n').map(l => l.trim()).filter(Boolean);
      if (lines.length <= 1) return;

      const parsed: Student[] = [];
      for (let i = 1; i < lines.length; i++) {
        const cols = lines[i].split(',').map(c => c.replace(/^"|"$/g, '').trim());
        if (cols.length >= 3 && cols[0]) {
          parsed.push({
            id: `STU-IMP-${Date.now()}-${i}`,
            name: cols[0],
            studentClass: cols[1] || '১ম শ্রেণি',
            section: 'ক',
            rollNumber: parseInt(cols[2]) || i,
            gender: cols[3] || 'ছাত্র',
            birthDate: cols[4] || '2019-01-01',
            mobile: cols[5] || '',
            parentContact: cols[5] || '',
            village: cols[6] || 'পশ্চিম রামপুর',
            fatherName: cols[7] || '',
            motherName: cols[8] || '',
            birthRegNumber: cols[9] || '',
            academicYear: '২০২৬',
            address: '',
            isSpecialNeeds: false,
            status: 'Current',
            customValues: {},
            admissionDate: getTodayDateStr(),
            lastModifiedDate: getTodayDateStr(),
            createdAt: Date.now(),
            updatedAt: Date.now()
          });
        }
      }
      setImportPreview(parsed);
    } catch (e) {
      showToast('CSV ফাইল পার্স করতে ত্রুটি হয়েছে', 'error');
    }
  };

  const handleConfirmImport = () => {
    if (importPreview.length === 0) return;
    importStudents(importPreview);
    onClose();
  };

  return (
    <div className="fixed inset-0 z-50 overflow-y-auto bg-slate-900/60 backdrop-blur-xs flex items-center justify-center p-4">
      <div className="bg-white rounded-2xl shadow-2xl max-w-xl w-full overflow-hidden border border-slate-200 animate-in fade-in zoom-in-95 duration-200">
        <div className="bg-emerald-800 text-white px-5 py-4 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <FileSpreadsheet className="w-5 h-5" />
            <h3 className="font-bold text-sm font-serif-bn">শিক্ষার্থী ইম্পোর্ট ও এক্সপোর্ট</h3>
          </div>
          <button onClick={onClose} className="text-emerald-200 hover:text-white">
            <X className="w-4 h-4" />
          </button>
        </div>

        <div className="p-6 space-y-6">
          {/* Export Section */}
          <div className="p-4 rounded-xl bg-slate-50 border border-slate-200/80 space-y-3">
            <h4 className="font-bold text-xs text-slate-800 uppercase tracking-wider">
              ডাটা এক্সপোর্ট (ডাউনলোড)
            </h4>
            <p className="text-xs text-slate-500">
              বর্তমান ডাটাবেজের সকল শিক্ষার্থীর তথ্য CSV ফরম্যাটে সংরক্ষণ করুন।
            </p>
            <div className="flex gap-2">
              <button
                onClick={handleExportCSV}
                className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-lg bg-emerald-600 hover:bg-emerald-700 text-white text-xs font-semibold cursor-pointer shadow-xs"
              >
                <Download className="w-4 h-4" />
                <span>সকল শিক্ষার্থী এক্সপোর্ট করুন (CSV)</span>
              </button>
              <button
                onClick={handleDownloadTemplate}
                className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-lg border border-slate-300 bg-white hover:bg-slate-50 text-slate-700 text-xs font-medium cursor-pointer"
              >
                <FileSpreadsheet className="w-4 h-4" />
                <span>নমুনা টেমপ্লেট</span>
              </button>
            </div>
          </div>

          {/* Import Section */}
          <div className="p-4 rounded-xl bg-slate-50 border border-slate-200/80 space-y-3">
            <h4 className="font-bold text-xs text-slate-800 uppercase tracking-wider">
              CSV ফাইল থেকে ইম্পোর্ট করুন
            </h4>
            <p className="text-xs text-slate-500">
              কম্পিউটার বা এক্সেল শিট থেকে শিক্ষার্থীদের তালিকা একসাথে যুক্ত করুন।
            </p>

            <input
              type="file"
              accept=".csv"
              onChange={handleFileUpload}
              className="block w-full text-xs text-slate-500 file:mr-4 file:py-2 file:px-4 file:rounded-lg file:border-0 file:text-xs file:font-semibold file:bg-emerald-50 file:text-emerald-700 hover:file:bg-emerald-100"
            />

            {importPreview.length > 0 && (
              <div className="p-3 rounded-lg bg-emerald-50 border border-emerald-200 text-emerald-800 text-xs space-y-2">
                <div className="flex items-center justify-between font-bold">
                  <span>ইম্পোর্টের জন্য প্রস্তুত: {importPreview.length} জন শিক্ষার্থী</span>
                </div>
                <div className="max-h-32 overflow-y-auto divide-y divide-emerald-200/60 text-[11px]">
                  {importPreview.slice(0, 5).map((p, i) => (
                    <div key={i} className="py-1 flex justify-between">
                      <span>{p.name} ({p.studentClass})</span>
                      <span>রোল: {p.rollNumber}</span>
                    </div>
                  ))}
                  {importPreview.length > 5 && (
                    <div className="py-1 text-slate-500 italic text-center">
                      + আরও {importPreview.length - 5} জন শিক্ষার্থী...
                    </div>
                  )}
                </div>
              </div>
            )}
          </div>

          <div className="flex items-center justify-end gap-3 pt-2 border-t border-slate-200">
            <button
              onClick={onClose}
              className="px-4 py-2 rounded-lg border border-slate-300 text-slate-700 text-xs font-medium cursor-pointer"
            >
              বন্ধ করুন
            </button>
            {importPreview.length > 0 && (
              <button
                onClick={handleConfirmImport}
                className="px-4 py-2 rounded-lg bg-emerald-600 hover:bg-emerald-700 text-white text-xs font-bold transition-colors cursor-pointer"
              >
                ইম্পোর্ট সম্পন্ন করুন ({importPreview.length} জন)
              </button>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};
