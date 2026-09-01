import React, { useState, useMemo } from 'react';
import {
  UserCheck,
  Calendar,
  ChevronLeft,
  ChevronRight,
  Printer,
  Plus,
  Trash2,
  CheckCircle2,
  TrendingUp,
  Clock,
  ArrowLeft,
  Users,
  FileText
} from 'lucide-react';
import { useApp } from '../../context/AppContext';
import {
  toBanglaDigits,
  formatBanglaDate,
  getTodayDateStr,
  banglaMonths
} from '../../utils/banglaUtils';

export const AttendanceReportTool: React.FC<{ onBack: () => void }> = ({ onBack }) => {
  const {
    schoolInfo,
    students,
    attendanceRecords,
    addAttendanceRecord,
    deleteAttendanceRecord,
    genderTerminology
  } = useApp();

  const [activeTab, setActiveTab] = useState<'daily' | 'monthly' | 'history'>('daily');
  const [selectedDate, setSelectedDate] = useState(getTodayDateStr());

  // Month & Year state
  const currentDate = new Date();
  const [selectedMonth, setSelectedMonth] = useState(currentDate.getMonth()); // 0-11
  const [selectedYear, setSelectedYear] = useState(currentDate.getFullYear());

  // Daily entry state for classes
  const standardClasses = [
    'প্রাক-প্রাথমিক ৪+',
    'প্রাক-প্রাথমিক ৫+',
    '১ম শ্রেণি',
    '২য় শ্রেণি',
    '৩য় শ্রেণি',
    '৪র্থ শ্রেণি',
    '৫ম শ্রেণি'
  ];

  // Pre-calculate enrolled counts per class from students database
  const enrolledByClass = useMemo(() => {
    const map: Record<string, { totalBoys: number; totalGirls: number; total: number }> = {};
    standardClasses.forEach(cls => {
      map[cls] = { totalBoys: 0, totalGirls: 0, total: 0 };
    });

    students.forEach(s => {
      const isBoy = s.gender === 'ছাত্র' || s.gender === 'বালক' || s.gender === 'ছেলে';
      const isGirl = s.gender === 'ছাত্রী' || s.gender === 'বালিকা' || s.gender === 'মেয়ে';
      const cls = s.studentClass;
      if (map[cls]) {
        map[cls].total++;
        if (isBoy) map[cls].totalBoys++;
        if (isGirl) map[cls].totalGirls++;
      }
    });

    return map;
  }, [students]);

  // Form state for saving attendance on selected date
  const [classAttendanceInputs, setClassAttendanceInputs] = useState<
    Record<string, { presentBoys: number; presentGirls: number; notes: string }>
  >(() => {
    const initial: Record<string, { presentBoys: number; presentGirls: number; notes: string }> = {};
    standardClasses.forEach(cls => {
      const counts = enrolledByClass[cls] || { totalBoys: 10, totalGirls: 10 };
      initial[cls] = {
        presentBoys: counts.totalBoys,
        presentGirls: counts.totalGirls,
        notes: ''
      };
    });
    return initial;
  });

  // Load existing records for selectedDate
  const recordsForDate = attendanceRecords.filter(r => r.date === selectedDate);

  const handleSaveAllDaily = () => {
    standardClasses.forEach(cls => {
      const enrolled = enrolledByClass[cls] || { totalBoys: 0, totalGirls: 0 };
      const input = classAttendanceInputs[cls] || {
        presentBoys: enrolled.totalBoys,
        presentGirls: enrolled.totalGirls,
        notes: ''
      };

      const presentB = Math.min(input.presentBoys, enrolled.totalBoys);
      const presentG = Math.min(input.presentGirls, enrolled.totalGirls);

      addAttendanceRecord({
        date: selectedDate,
        className: cls,
        presentBoys: presentB,
        presentGirls: presentG,
        absentBoys: Math.max(0, enrolled.totalBoys - presentB),
        absentGirls: Math.max(0, enrolled.totalGirls - presentG),
        totalBoys: enrolled.totalBoys,
        totalGirls: enrolled.totalGirls,
        notes: input.notes
      });
    });
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
                হাজিরা ও মাসিক উপস্থিতি রিপোর্ট
              </h1>
              <span className="text-xs px-2.5 py-0.5 rounded-full bg-emerald-100 text-emerald-800 font-bold">
                ডিজিটাল খাতা
              </span>
            </div>
            <p className="text-xs text-slate-500 mt-0.5">
              দৈনিক শ্রেণিভিত্তিক ছাত্র-ছাত্রী হাজিরা রেজিস্টার ও স্বয়ংক্রিয় মাসিক বিবরণী
            </p>
          </div>
        </div>

        <div className="flex items-center gap-2">
          <button
            onClick={handlePrint}
            className="inline-flex items-center gap-1.5 px-4 py-2 rounded-lg border border-slate-300 bg-white hover:bg-slate-50 text-slate-700 text-xs font-semibold cursor-pointer shadow-xs"
          >
            <Printer className="w-4 h-4" />
            <span>প্রিন্ট / রিপোর্ট</span>
          </button>
        </div>
      </div>

      {/* Tab Navigation */}
      <div className="flex border-b border-slate-200 bg-white px-5 rounded-2xl border shadow-xs gap-6">
        <button
          onClick={() => setActiveTab('daily')}
          className={`py-3.5 text-xs font-bold transition-all border-b-2 cursor-pointer flex items-center gap-2 ${
            activeTab === 'daily'
              ? 'border-emerald-600 text-emerald-700'
              : 'border-transparent text-slate-500 hover:text-slate-800'
          }`}
        >
          <UserCheck className="w-4 h-4" />
          <span>দৈনিক হাজিরা গ্রহণ</span>
        </button>

        <button
          onClick={() => setActiveTab('monthly')}
          className={`py-3.5 text-xs font-bold transition-all border-b-2 cursor-pointer flex items-center gap-2 ${
            activeTab === 'monthly'
              ? 'border-emerald-600 text-emerald-700'
              : 'border-transparent text-slate-500 hover:text-slate-800'
          }`}
        >
          <Calendar className="w-4 h-4" />
          <span>মাসিক হাজিরা বিবরণী</span>
        </button>

        <button
          onClick={() => setActiveTab('history')}
          className={`py-3.5 text-xs font-bold transition-all border-b-2 cursor-pointer flex items-center gap-2 ${
            activeTab === 'history'
              ? 'border-emerald-600 text-emerald-700'
              : 'border-transparent text-slate-500 hover:text-slate-800'
          }`}
        >
          <Clock className="w-4 h-4" />
          <span>হাজিরা ইতিহাস</span>
        </button>
      </div>

      {/* TAB 1: DAILY ATTENDANCE */}
      {activeTab === 'daily' && (
        <div className="space-y-6">
          {/* Date Picker Bar */}
          <div className="bg-white p-4 rounded-2xl border border-slate-200/80 shadow-xs flex flex-wrap items-center justify-between gap-4">
            <div className="flex items-center gap-3">
              <label className="text-xs font-bold text-slate-700">হাজিরার তারিখ:</label>
              <input
                type="date"
                value={selectedDate}
                onChange={e => setSelectedDate(e.target.value)}
                className="px-3 py-1.5 rounded-lg border border-slate-300 text-xs font-bold text-slate-800 bg-slate-50 focus:bg-white"
              />
              <span className="text-xs font-semibold text-emerald-700 bg-emerald-50 px-3 py-1 rounded-lg">
                {formatBanglaDate(selectedDate)}
              </span>
            </div>

            <button
              onClick={handleSaveAllDaily}
              className="inline-flex items-center gap-2 px-5 py-2 rounded-lg bg-emerald-600 hover:bg-emerald-700 text-white text-xs font-bold shadow-md shadow-emerald-600/20 cursor-pointer"
            >
              <CheckCircle2 className="w-4 h-4" />
              <span>আজকের সকল শ্রেণি সংরক্ষণ করুন</span>
            </button>
          </div>

          {/* Class Attendance Grid */}
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {standardClasses.map(clsName => {
              const enrolled = enrolledByClass[clsName] || { totalBoys: 0, totalGirls: 0, total: 0 };
              const currentInput = classAttendanceInputs[clsName] || {
                presentBoys: enrolled.totalBoys,
                presentGirls: enrolled.totalGirls,
                notes: ''
              };

              const existing = recordsForDate.find(r => r.className === clsName);
              const presentB = existing ? existing.presentBoys : currentInput.presentBoys;
              const presentG = existing ? existing.presentGirls : currentInput.presentGirls;
              const totalP = presentB + presentG;
              const totalE = enrolled.total;
              const pct = totalE > 0 ? Math.round((totalP / totalE) * 100) : 0;

              return (
                <div
                  key={clsName}
                  className="bg-white rounded-2xl border border-slate-200/80 p-5 shadow-xs space-y-4 hover:border-emerald-300 transition-all"
                >
                  <div className="flex items-center justify-between border-b border-slate-100 pb-2.5">
                    <h3 className="font-bold text-sm text-slate-800 font-serif-bn">{clsName}</h3>
                    <span className="text-xs px-2 py-0.5 rounded-full bg-slate-100 text-slate-700 font-semibold">
                      মোট শিক্ষার্থী: {toBanglaDigits(totalE)} জন
                    </span>
                  </div>

                  {/* Attendance Percentage Indicator */}
                  <div className="space-y-1">
                    <div className="flex justify-between text-xs font-semibold">
                      <span className="text-slate-600">উপস্থিতি: {toBanglaDigits(totalP)} জন</span>
                      <span className="text-emerald-700 font-bold">{toBanglaDigits(pct)}%</span>
                    </div>
                    <div className="w-full h-2 rounded-full bg-slate-100 overflow-hidden">
                      <div
                        style={{ width: `${pct}%` }}
                        className="bg-gradient-to-r from-emerald-500 to-teal-500 h-full rounded-full transition-all"
                      />
                    </div>
                  </div>

                  {/* Boys & Girls Inputs */}
                  <div className="grid grid-cols-2 gap-3 text-xs">
                    {/* Boys */}
                    <div className="p-3 rounded-xl bg-sky-50/60 border border-sky-200/60 space-y-1.5">
                      <div className="flex justify-between font-semibold text-sky-800">
                        <span>{genderTerminology.boyLabel} (মোট: {toBanglaDigits(enrolled.totalBoys)})</span>
                      </div>
                      <div className="flex items-center gap-1.5">
                        <span className="text-[11px] text-slate-500">উপস্থিত:</span>
                        <input
                          type="number"
                          min={0}
                          max={enrolled.totalBoys}
                          value={currentInput.presentBoys}
                          onChange={e => {
                            const val = parseInt(e.target.value) || 0;
                            setClassAttendanceInputs(prev => ({
                              ...prev,
                              [clsName]: { ...prev[clsName], presentBoys: val }
                            }));
                          }}
                          className="w-16 px-2 py-1 rounded-md border border-sky-300 bg-white font-bold text-slate-800 text-center"
                        />
                      </div>
                    </div>

                    {/* Girls */}
                    <div className="p-3 rounded-xl bg-pink-50/60 border border-pink-200/60 space-y-1.5">
                      <div className="flex justify-between font-semibold text-pink-800">
                        <span>{genderTerminology.girlLabel} (মোট: {toBanglaDigits(enrolled.totalGirls)})</span>
                      </div>
                      <div className="flex items-center gap-1.5">
                        <span className="text-[11px] text-slate-500">উপস্থিত:</span>
                        <input
                          type="number"
                          min={0}
                          max={enrolled.totalGirls}
                          value={currentInput.presentGirls}
                          onChange={e => {
                            const val = parseInt(e.target.value) || 0;
                            setClassAttendanceInputs(prev => ({
                              ...prev,
                              [clsName]: { ...prev[clsName], presentGirls: val }
                            }));
                          }}
                          className="w-16 px-2 py-1 rounded-md border border-pink-300 bg-white font-bold text-slate-800 text-center"
                        />
                      </div>
                    </div>
                  </div>

                  {/* Notes / Reason */}
                  <input
                    type="text"
                    placeholder="মন্তব্য (যেমন: বৃষ্টিজনিত অনুপস্থিতি)"
                    value={currentInput.notes}
                    onChange={e =>
                      setClassAttendanceInputs(prev => ({
                        ...prev,
                        [clsName]: { ...prev[clsName], notes: e.target.value }
                      }))
                    }
                    className="w-full px-3 py-1.5 text-xs rounded-lg border border-slate-200 bg-slate-50 focus:bg-white outline-hidden"
                  />
                </div>
              );
            })}
          </div>
        </div>
      )}

      {/* TAB 2: MONTHLY ATTENDANCE REPORT */}
      {activeTab === 'monthly' && (
        <div className="space-y-6">
          {/* Month Selector */}
          <div className="bg-white p-4 rounded-2xl border border-slate-200/80 shadow-xs flex flex-wrap items-center justify-between gap-4">
            <div className="flex items-center gap-2">
              <label className="text-xs font-bold text-slate-700">মাস ও বছর:</label>
              <select
                value={selectedMonth}
                onChange={e => setSelectedMonth(parseInt(e.target.value))}
                className="px-3 py-1.5 rounded-lg border border-slate-300 text-xs font-semibold bg-slate-50"
              >
                {banglaMonths.map((m, i) => (
                  <option key={m} value={i}>
                    {m}
                  </option>
                ))}
              </select>
              <select
                value={selectedYear}
                onChange={e => setSelectedYear(parseInt(e.target.value))}
                className="px-3 py-1.5 rounded-lg border border-slate-300 text-xs font-semibold bg-slate-50"
              >
                {[2024, 2025, 2026, 2027].map(y => (
                  <option key={y} value={y}>
                    {toBanglaDigits(y)}
                  </option>
                ))}
              </select>
            </div>

            <div className="text-xs text-slate-500">
              নির্বাচিত মাস: <strong className="text-slate-800">{banglaMonths[selectedMonth]}, {toBanglaDigits(selectedYear)}</strong>
            </div>
          </div>

          {/* Printable Monthly Summary Sheet */}
          <div className="bg-white p-6 rounded-2xl border border-slate-200/80 shadow-xs space-y-6">
            {/* Sheet Header */}
            <div className="text-center space-y-1 border-b border-slate-200 pb-4">
              <h2 className="text-lg font-bold font-serif-bn text-slate-900">
                {schoolInfo.schoolName}
              </h2>
              <p className="text-xs text-slate-600">{schoolInfo.address}</p>
              <h3 className="text-sm font-bold text-emerald-800 pt-1">
                মাসিক শিক্ষার্থী উপস্থিতি বিবরণী — {banglaMonths[selectedMonth]}, {toBanglaDigits(selectedYear)}
              </h3>
            </div>

            {/* Table */}
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs border-collapse">
                <thead>
                  <tr className="bg-slate-100 text-slate-700 font-bold border border-slate-300 text-center">
                    <th className="p-2.5 border border-slate-300 text-left">শ্রেণি</th>
                    <th className="p-2.5 border border-slate-300">মোট ছাত্র</th>
                    <th className="p-2.5 border border-slate-300">মোট ছাত্রী</th>
                    <th className="p-2.5 border border-slate-300">মোট শিক্ষার্থী</th>
                    <th className="p-2.5 border border-slate-300">গড় ছাত্র উপস্থিতি</th>
                    <th className="p-2.5 border border-slate-300">গড় ছাত্রী উপস্থিতি</th>
                    <th className="p-2.5 border border-slate-300">মোট গড় উপস্থিতি</th>
                    <th className="p-2.5 border border-slate-300">উপস্থিতির হার (%)</th>
                  </tr>
                </thead>
                <tbody>
                  {standardClasses.map(clsName => {
                    const enrolled = enrolledByClass[clsName] || { totalBoys: 0, totalGirls: 0, total: 0 };
                    // Calculate from records in this month
                    const monthPrefix = `${selectedYear}-${String(selectedMonth + 1).padStart(2, '0')}`;
                    const classMonthRecords = attendanceRecords.filter(
                      r => r.className === clsName && r.date.startsWith(monthPrefix)
                    );

                    const daysCount = classMonthRecords.length || 1;
                    const avgBoys = Math.round(
                      classMonthRecords.reduce((acc, r) => acc + r.presentBoys, 0) / daysCount
                    ) || enrolled.totalBoys;
                    const avgGirls = Math.round(
                      classMonthRecords.reduce((acc, r) => acc + r.presentGirls, 0) / daysCount
                    ) || enrolled.totalGirls;
                    const avgTotal = avgBoys + avgGirls;
                    const rate = enrolled.total > 0 ? Math.round((avgTotal / enrolled.total) * 100) : 0;

                    return (
                      <tr key={clsName} className="hover:bg-slate-50 text-center">
                        <td className="p-2.5 border border-slate-300 font-bold text-slate-900 text-left">
                          {clsName}
                        </td>
                        <td className="p-2.5 border border-slate-300 font-medium">
                          {toBanglaDigits(enrolled.totalBoys)}
                        </td>
                        <td className="p-2.5 border border-slate-300 font-medium">
                          {toBanglaDigits(enrolled.totalGirls)}
                        </td>
                        <td className="p-2.5 border border-slate-300 font-bold text-slate-800">
                          {toBanglaDigits(enrolled.total)}
                        </td>
                        <td className="p-2.5 border border-slate-300 text-sky-700 font-semibold">
                          {toBanglaDigits(avgBoys)}
                        </td>
                        <td className="p-2.5 border border-slate-300 text-pink-700 font-semibold">
                          {toBanglaDigits(avgGirls)}
                        </td>
                        <td className="p-2.5 border border-slate-300 font-bold text-emerald-800">
                          {toBanglaDigits(avgTotal)}
                        </td>
                        <td className="p-2.5 border border-slate-300 font-bold">
                          <span
                            className={`px-2 py-0.5 rounded-full text-xs ${
                              rate >= 80 ? 'bg-emerald-100 text-emerald-800' : 'bg-amber-100 text-amber-800'
                            }`}
                          >
                            {toBanglaDigits(rate)}%
                          </span>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>

            {/* Official Signatures Row */}
            <div className="pt-12 flex justify-between items-end text-xs text-slate-700 px-6">
              <div className="text-center space-y-1">
                <div className="w-36 border-t border-slate-400" />
                <span className="font-semibold block">শ্রেণি শিক্ষক</span>
              </div>
              <div className="text-center space-y-1">
                <div className="w-36 border-t border-slate-400" />
                <span className="font-bold block">প্রধান শিক্ষক</span>
                <span className="text-[11px] text-slate-500">{schoolInfo.schoolName}</span>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* TAB 3: ATTENDANCE HISTORY */}
      {activeTab === 'history' && (
        <div className="bg-white rounded-2xl border border-slate-200/80 shadow-xs overflow-hidden">
          <div className="p-4 border-b border-slate-100">
            <h3 className="font-bold text-sm text-slate-800">সংরক্ষিত হাজিরা রেকর্ডসমূহ</h3>
          </div>
          {attendanceRecords.length === 0 ? (
            <div className="p-8 text-center text-slate-400 text-xs">
              কোন হাজিরা রেকর্ড এখনো সংরক্ষিত হয়নি
            </div>
          ) : (
            <div className="divide-y divide-slate-100 max-h-[600px] overflow-y-auto">
              {attendanceRecords.map(r => (
                <div key={r.id} className="p-4 flex items-center justify-between hover:bg-slate-50">
                  <div className="space-y-1">
                    <div className="flex items-center gap-2">
                      <span className="font-bold text-xs text-slate-800">{r.className}</span>
                      <span className="text-xs text-slate-500">• {formatBanglaDate(r.date)}</span>
                    </div>
                    <div className="text-xs text-slate-600">
                      উপস্থিত: <strong className="text-emerald-700">{toBanglaDigits(r.presentBoys + r.presentGirls)}</strong> / মোট {toBanglaDigits(r.totalBoys + r.totalGirls)} (ছাত্র: {toBanglaDigits(r.presentBoys)}, ছাত্রী: {toBanglaDigits(r.presentGirls)})
                    </div>
                    {r.notes && (
                      <div className="text-[11px] text-slate-400 italic">মন্তব্য: {r.notes}</div>
                    )}
                  </div>
                  <button
                    onClick={() => deleteAttendanceRecord(r.id)}
                    className="p-2 rounded-lg text-rose-500 hover:text-rose-700 hover:bg-rose-50 transition-colors"
                  >
                    <Trash2 className="w-4 h-4" />
                  </button>
                </div>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
};
