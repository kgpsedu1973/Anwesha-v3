import React, { useState } from 'react';
import {
  Settings,
  School,
  Save,
  RotateCcw,
  Download,
  Upload,
  Sparkles,
  MapPin,
  Users,
  Database,
  CheckCircle2,
  AlertTriangle
} from 'lucide-react';
import { useApp } from '../../context/AppContext';
import { SchoolInfo, GenderTerminology } from '../../types';
import { getTodayDateStr } from '../../utils/banglaUtils';

export const SettingsScreen: React.FC = () => {
  const {
    schoolInfo,
    updateSchoolInfo,
    genderTerminology,
    updateGenderTerminology,
    students,
    customFields,
    formulaRules,
    attendanceRecords,
    studentDocuments,
    showToast
  } = useApp();

  const [formData, setFormData] = useState<SchoolInfo>({ ...schoolInfo });
  const [villagesText, setVillagesText] = useState(schoolInfo.internalVillages.join(', '));
  const [genderData, setGenderData] = useState<GenderTerminology>({ ...genderTerminology });

  const handleSaveSchoolInfo = (e: React.FormEvent) => {
    e.preventDefault();
    const villages = villagesText
      .split(',')
      .map(v => v.trim())
      .filter(Boolean);

    updateSchoolInfo({
      ...formData,
      internalVillages: villages
    });
    updateGenderTerminology(genderData);
    showToast('বিদ্যালয়ের সেটিংস সফলভাবে সংরক্ষিত হয়েছে');
  };

  const handleExportFullBackup = () => {
    const backupData = {
      version: '3.0.0',
      exportedAt: new Date().toISOString(),
      schoolInfo: { ...formData, internalVillages: villagesText.split(',').map(v => v.trim()).filter(Boolean) },
      genderTerminology: genderData,
      students,
      customFields,
      formulaRules,
      attendanceRecords,
      studentDocuments
    };

    const blob = new Blob([JSON.stringify(backupData, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `anwesha_school_backup_${getTodayDateStr()}.json`;
    a.click();
    URL.revokeObjectURL(url);
    showToast('সম্পূর্ণ বিদ্যালয় ডেটাবেজ ব্যাকআপ ডাউনলোড হয়েছে');
  };

  const handleRestoreBackup = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    const reader = new FileReader();
    reader.onload = event => {
      try {
        const json = JSON.parse(event.target?.result as string);
        if (json.students && Array.isArray(json.students)) {
          localStorage.setItem('anwesha_students', JSON.stringify(json.students));
          if (json.schoolInfo) localStorage.setItem('anwesha_school_info', JSON.stringify(json.schoolInfo));
          if (json.customFields) localStorage.setItem('anwesha_custom_fields', JSON.stringify(json.customFields));
          if (json.formulaRules) localStorage.setItem('anwesha_formula_rules', JSON.stringify(json.formulaRules));
          if (json.attendanceRecords) localStorage.setItem('anwesha_attendance_records', JSON.stringify(json.attendanceRecords));
          if (json.studentDocuments) localStorage.setItem('anwesha_student_documents', JSON.stringify(json.studentDocuments));
          
          showToast('ব্যাকআপ সফলভাবে রিস্টোর হয়েছে! পেজ রিলোড হচ্ছে...');
          setTimeout(() => window.location.reload(), 1000);
        } else {
          showToast('অবৈধ ব্যাকআপ ফাইল ফরম্যাট', 'error');
        }
      } catch (err) {
        showToast('ব্যাকআপ ফাইল পার্স করতে ব্যর্থ হয়েছে', 'error');
      }
    };
    reader.readAsText(file);
  };

  const handleResetToSample = () => {
    if (confirm('আপনি কি সকল ডেটা মুছে পুনরায় প্রাথমিক ডেমো ডেটা লোড করতে চান?')) {
      localStorage.clear();
      showToast('সকল ডেটা রিসেট হয়েছে! পেজ রিলোড হচ্ছে...');
      setTimeout(() => window.location.reload(), 1000);
    }
  };

  return (
    <div className="space-y-6 pb-16 max-w-4xl mx-auto">
      {/* Header */}
      <div className="bg-white p-5 rounded-2xl border border-slate-200/80 shadow-xs flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <h1 className="text-xl font-bold text-slate-900 font-serif-bn">
              বিদ্যালয় সেটিংস ও কনফিগারেশন
            </h1>
            <span className="text-xs px-2.5 py-0.5 rounded-full bg-slate-100 text-slate-800 font-bold">
              সিস্টেম কন্ট্রোল
            </span>
          </div>
          <p className="text-xs text-slate-500 mt-0.5">
            প্রাতিষ্ঠানিক পরিচিতি, ক্যাচমেন্ট এরিয়া গ্রাম নির্ধারণ ও পূর্ণাঙ্গ ডেটাবেজ ব্যাকআপ
          </p>
        </div>
      </div>

      <form onSubmit={handleSaveSchoolInfo} className="space-y-6">
        {/* School Information Block */}
        <div className="bg-white p-6 rounded-2xl border border-slate-200/80 shadow-xs space-y-4">
          <h3 className="font-bold text-sm text-slate-800 border-b border-slate-100 pb-3 flex items-center gap-2">
            <School className="w-4 h-4 text-emerald-600" />
            <span>বিদ্যালয়ের পরিচিতি ও প্রাতিষ্ঠানিক তথ্য</span>
          </h3>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 text-xs">
            <div className="sm:col-span-2">
              <label className="block font-semibold text-slate-700 mb-1">
                বিদ্যালয়ের পূর্ণ নাম <span className="text-rose-500">*</span>
              </label>
              <input
                type="text"
                required
                value={formData.schoolName}
                onChange={e => setFormData({ ...formData, schoolName: e.target.value })}
                className="w-full px-3 py-2 rounded-lg border border-slate-300 bg-slate-50 text-sm font-semibold text-slate-800 focus:bg-white"
              />
            </div>

            <div className="sm:col-span-2">
              <label className="block font-semibold text-slate-700 mb-1">
                ঠিকানা (গ্রাম, ডাকঘর, উপজেলা, জেলা)
              </label>
              <input
                type="text"
                value={formData.address}
                onChange={e => setFormData({ ...formData, address: e.target.value })}
                className="w-full px-3 py-2 rounded-lg border border-slate-300 bg-slate-50 text-slate-800 focus:bg-white"
              />
            </div>

            <div>
              <label className="block font-semibold text-slate-700 mb-1">ইআইআইএন (EIIN)</label>
              <input
                type="text"
                value={formData.eiin}
                onChange={e => setFormData({ ...formData, eiin: e.target.value })}
                className="w-full px-3 py-2 rounded-lg border border-slate-300 bg-slate-50 font-mono"
              />
            </div>

            <div>
              <label className="block font-semibold text-slate-700 mb-1">বিদ্যালয় কোড (EMIS Code)</label>
              <input
                type="text"
                value={formData.schoolCode}
                onChange={e => setFormData({ ...formData, schoolCode: e.target.value })}
                className="w-full px-3 py-2 rounded-lg border border-slate-300 bg-slate-50 font-mono"
              />
            </div>

            <div>
              <label className="block font-semibold text-slate-700 mb-1">প্রধান শিক্ষকের নাম</label>
              <input
                type="text"
                value={formData.headTeacherName}
                onChange={e => setFormData({ ...formData, headTeacherName: e.target.value })}
                className="w-full px-3 py-2 rounded-lg border border-slate-300 bg-slate-50"
              />
            </div>

            <div>
              <label className="block font-semibold text-slate-700 mb-1">যোগাযোগের মোবাইল</label>
              <input
                type="text"
                value={formData.contactPhone}
                onChange={e => setFormData({ ...formData, contactPhone: e.target.value })}
                className="w-full px-3 py-2 rounded-lg border border-slate-300 bg-slate-50"
              />
            </div>
          </div>
        </div>

        {/* Catchment Villages */}
        <div className="bg-white p-6 rounded-2xl border border-slate-200/80 shadow-xs space-y-4">
          <h3 className="font-bold text-sm text-slate-800 border-b border-slate-100 pb-3 flex items-center gap-2">
            <MapPin className="w-4 h-4 text-indigo-600" />
            <span>ক্যাচমেন্ট এরিয়া ও অভ্যন্তরীণ গ্রামসমূহ (সূত্রের জন্য)</span>
          </h3>

          <div className="space-y-2 text-xs">
            <label className="block font-semibold text-slate-700">
              অভ্যন্তরীণ গ্রামসমূহ (কমা দিয়ে আলাদা করুন)
            </label>
            <input
              type="text"
              value={villagesText}
              onChange={e => setVillagesText(e.target.value)}
              placeholder="যেমন: পশ্চিম রামপুর, পূর্ব রামপুর, আমতলী"
              className="w-full px-3 py-2 rounded-lg border border-slate-300 bg-slate-50 text-slate-800 focus:bg-white"
            />
            <p className="text-[11px] text-slate-500 leading-relaxed">
              * এই তালিকার অন্তর্ভুক্ত গ্রামের শিক্ষার্থীরা স্বয়ংক্রিয়ভাবে <strong>অভ্যন্তরীণ</strong> ক্যাটাগরির অন্তর্ভুক্ত হবে এবং অবশিষ্টরা <strong>বহিরাগত</strong> হিসেবে গণ্য হবে।
            </p>
          </div>
        </div>

        {/* Gender Terminology */}
        <div className="bg-white p-6 rounded-2xl border border-slate-200/80 shadow-xs space-y-4">
          <h3 className="font-bold text-sm text-slate-800 border-b border-slate-100 pb-3 flex items-center gap-2">
            <Users className="w-4 h-4 text-sky-600" />
            <span>লিঙ্গভিত্তিক প্রদর্শন টার্মিনোলজি</span>
          </h3>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 text-xs">
            <div>
              <label className="block font-semibold text-slate-700 mb-1">বালক / ছেলের লেবেল</label>
              <input
                type="text"
                value={genderData.boyLabel}
                onChange={e => setGenderData({ ...genderData, boyLabel: e.target.value })}
                className="w-full px-3 py-2 rounded-lg border border-slate-300 bg-slate-50"
              />
            </div>
            <div>
              <label className="block font-semibold text-slate-700 mb-1">বালিকা / মেয়ের লেবেল</label>
              <input
                type="text"
                value={genderData.girlLabel}
                onChange={e => setGenderData({ ...genderData, girlLabel: e.target.value })}
                className="w-full px-3 py-2 rounded-lg border border-slate-300 bg-slate-50"
              />
            </div>
          </div>
        </div>

        {/* Save Button */}
        <div className="flex justify-end">
          <button
            type="submit"
            className="inline-flex items-center gap-2 px-6 py-2.5 rounded-xl bg-emerald-600 hover:bg-emerald-700 text-white text-xs font-bold shadow-md shadow-emerald-600/20 cursor-pointer"
          >
            <Save className="w-4 h-4" />
            <span>সেটিংস পরিবর্তন সংরক্ষণ করুন</span>
          </button>
        </div>
      </form>

      {/* Database Backup & Disaster Recovery */}
      <div className="bg-white p-6 rounded-2xl border border-slate-200/80 shadow-xs space-y-4">
        <h3 className="font-bold text-sm text-slate-800 border-b border-slate-100 pb-3 flex items-center gap-2">
          <Database className="w-4 h-4 text-rose-600" />
          <span>ডেটা ব্যাকআপ, রিস্টোর ও রিসেট ব্যবস্থাপনা</span>
        </h3>

        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
          <div className="p-4 rounded-xl bg-slate-50 border border-slate-200 space-y-2 text-xs">
            <span className="font-bold text-slate-800 block">পূর্ণাঙ্গ ব্যাকআপ এক্সপোর্ট</span>
            <p className="text-slate-500 text-[11px]">
              বিদ্যালয়ের সকল শিক্ষার্থী, হাজিরা ও কাস্টম ফিল্ডের JSON ব্যাকআপ ফাইল ডাউনলোড করুন।
            </p>
            <button
              onClick={handleExportFullBackup}
              className="w-full inline-flex items-center justify-center gap-1.5 px-3 py-2 rounded-lg bg-emerald-600 hover:bg-emerald-700 text-white font-semibold cursor-pointer"
            >
              <Download className="w-4 h-4" />
              <span>ব্যাকআপ ডাউনলোড</span>
            </button>
          </div>

          <div className="p-4 rounded-xl bg-slate-50 border border-slate-200 space-y-2 text-xs">
            <span className="font-bold text-slate-800 block">ব্যাকআপ থেকে রিস্টোর</span>
            <p className="text-slate-500 text-[11px]">
              পূর্বে ডাউনলোডকৃত JSON ব্যাকআপ ফাইল থেকে সকল ডেটা পুনরায় ফিরিয়ে আনুন।
            </p>
            <label className="w-full inline-flex items-center justify-center gap-1.5 px-3 py-2 rounded-lg border border-slate-300 bg-white hover:bg-slate-50 text-slate-700 font-semibold cursor-pointer">
              <Upload className="w-4 h-4" />
              <span>ফাইল আপলোড করুন</span>
              <input
                type="file"
                accept=".json"
                onChange={handleRestoreBackup}
                className="hidden"
              />
            </label>
          </div>

          <div className="p-4 rounded-xl bg-rose-50/50 border border-rose-200 space-y-2 text-xs">
            <span className="font-bold text-rose-800 block">সিস্টেম রিসেট</span>
            <p className="text-rose-600 text-[11px]">
              সকল কাস্টম ডেটা মুছে সিস্টেমকে প্রাথমিক ডেমো সংস্করণে ফেরত নিন।
            </p>
            <button
              onClick={handleResetToSample}
              className="w-full inline-flex items-center justify-center gap-1.5 px-3 py-2 rounded-lg bg-rose-600 hover:bg-rose-700 text-white font-semibold cursor-pointer"
            >
              <RotateCcw className="w-4 h-4" />
              <span>ডেমো ডেটা রিসেট</span>
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};
