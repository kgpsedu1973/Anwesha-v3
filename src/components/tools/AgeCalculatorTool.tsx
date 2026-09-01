import React, { useState, useMemo } from 'react';
import {
  Calendar,
  ArrowLeft,
  RotateCcw,
  Copy,
  Check,
  User,
  Sparkles,
  Clock,
  Heart,
  ChevronDown
} from 'lucide-react';
import { useApp } from '../../context/AppContext';
import {
  calculateAge,
  toBanglaDigits,
  getTodayDateStr,
  formatBanglaDate
} from '../../utils/banglaUtils';

export const AgeCalculatorTool: React.FC<{ onBack: () => void }> = ({ onBack }) => {
  const { students, showToast } = useApp();

  const [startDate, setStartDate] = useState('2018-05-15');
  const [endDate, setEndDate] = useState(getTodayDateStr());
  const [includeStartDay, setIncludeStartDay] = useState(false);
  const [includeEndDay, setIncludeEndDay] = useState(false);
  const [selectedStudentId, setSelectedStudentId] = useState<string>('');

  const [copied, setCopied] = useState(false);

  // Live age calculation
  const result = useMemo(() => {
    return calculateAge(startDate, endDate, includeStartDay, includeEndDay);
  }, [startDate, endDate, includeStartDay, includeEndDay]);

  const handleSelectStudent = (id: string) => {
    setSelectedStudentId(id);
    const student = students.find(s => s.id === id);
    if (student && student.birthDate) {
      setStartDate(student.birthDate);
      showToast(`শিক্ষার্থী ${student.name}-এর জন্মতারিখ লোড করা হয়েছে`);
    }
  };

  const handleReset = () => {
    setStartDate('2018-05-15');
    setEndDate(getTodayDateStr());
    setIncludeStartDay(false);
    setIncludeEndDay(false);
    setSelectedStudentId('');
    showToast('ক্যালকুলেটর রিসেট করা হয়েছে');
  };

  const handleCopy = () => {
    const text = `বয়স: ${result.formattedText}\nমোট দিন: ${toBanglaDigits(result.totalDays)} দিন | মোট সপ্তাহ: ${toBanglaDigits(result.totalWeeks)} সপ্তাহ | মোট মাস: ${toBanglaDigits(result.totalMonths)} মাস`;
    navigator.clipboard.writeText(text);
    setCopied(true);
    showToast('গণনার ফলাফল ক্লিপবোর্ডে কপি করা হয়েছে');
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className="space-y-6 pb-16 max-w-4xl mx-auto">
      {/* Header */}
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
                স্মার্ট বয়স ক্যালকুলেটর
              </h1>
              <span className="text-xs px-2.5 py-0.5 rounded-full bg-sky-100 text-sky-800 font-bold">
                লাইভ গণনা
              </span>
            </div>
            <p className="text-xs text-slate-500 mt-0.5">
              লাইভ বয়স, দিন-মাস ও দিন অন্তর্ভুক্তির নির্ভুল গণনা ও জন্মদিনের কাউন্টডাউন
            </p>
          </div>
        </div>

        <div className="flex items-center gap-2">
          <button
            onClick={handleReset}
            className="inline-flex items-center gap-1.5 px-3 py-2 rounded-lg border border-slate-300 bg-white hover:bg-slate-50 text-slate-700 text-xs font-semibold cursor-pointer"
          >
            <RotateCcw className="w-4 h-4" />
            <span>রিসেট</span>
          </button>
          <button
            onClick={handleCopy}
            className="inline-flex items-center gap-1.5 px-4 py-2 rounded-lg bg-sky-600 hover:bg-sky-700 text-white text-xs font-bold shadow-md shadow-sky-600/20 cursor-pointer"
          >
            {copied ? <Check className="w-4 h-4" /> : <Copy className="w-4 h-4" />}
            <span>কপি করুন</span>
          </button>
        </div>
      </div>

      {/* Main Calculation Card */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        {/* Controls Column */}
        <div className="bg-white p-5 rounded-2xl border border-slate-200/80 shadow-xs space-y-4 md:col-span-1">
          <h3 className="font-bold text-sm text-slate-800 border-b border-slate-100 pb-2 flex items-center gap-2">
            <User className="w-4 h-4 text-sky-600" />
            <span>তারিখ ও ইনপুট সেটিংস</span>
          </h3>

          {/* Student Quick Selector */}
          <div>
            <label className="block text-xs font-semibold text-slate-700 mb-1">
              শিক্ষার্থী তালিকা থেকে নির্বাচন
            </label>
            <select
              value={selectedStudentId}
              onChange={e => handleSelectStudent(e.target.value)}
              className="w-full px-3 py-2 text-xs rounded-lg border border-slate-300 bg-slate-50 focus:bg-white outline-hidden font-medium"
            >
              <option value="">ম্যানুয়াল তারিখ ইনপুট</option>
              {students.map(s => (
                <option key={s.id} value={s.id}>
                  {s.name} ({s.studentClass}, রোল {toBanglaDigits(s.rollNumber)})
                </option>
              ))}
            </select>
          </div>

          {/* Start Date */}
          <div>
            <label className="block text-xs font-semibold text-slate-700 mb-1">
              জন্মতারিখ / শুরুর তারিখ
            </label>
            <input
              type="date"
              value={startDate}
              onChange={e => {
                setStartDate(e.target.value);
                setSelectedStudentId('');
              }}
              className="w-full px-3 py-2 text-xs rounded-lg border border-slate-300 bg-slate-50 focus:bg-white outline-hidden font-bold text-slate-800"
            />
            <span className="text-[11px] text-slate-400 mt-1 block">
              {formatBanglaDate(startDate)}
            </span>
          </div>

          {/* Target End Date */}
          <div>
            <label className="block text-xs font-semibold text-slate-700 mb-1">
              গণনার শেষ তারিখ
            </label>
            <input
              type="date"
              value={endDate}
              onChange={e => setEndDate(e.target.value)}
              className="w-full px-3 py-2 text-xs rounded-lg border border-slate-300 bg-slate-50 focus:bg-white outline-hidden font-bold text-slate-800"
            />
            <span className="text-[11px] text-slate-400 mt-1 block">
              {formatBanglaDate(endDate)}
            </span>
          </div>

          {/* Inclusion Toggles */}
          <div className="p-3 rounded-xl bg-slate-50 border border-slate-200/80 space-y-2 text-xs">
            <span className="font-bold text-slate-700 block text-[11px]">দিন অন্তর্ভুক্তি অপশন:</span>
            <label className="flex items-center gap-2 cursor-pointer">
              <input
                type="checkbox"
                checked={includeStartDay}
                onChange={e => setIncludeStartDay(e.target.checked)}
                className="rounded-sm text-sky-600"
              />
              <span className="text-slate-600">প্রারম্ভিক দিন অন্তর্ভুক্ত (+১ দিন)</span>
            </label>
            <label className="flex items-center gap-2 cursor-pointer">
              <input
                type="checkbox"
                checked={includeEndDay}
                onChange={e => setIncludeEndDay(e.target.checked)}
                className="rounded-sm text-sky-600"
              />
              <span className="text-slate-600">শেষ দিন অন্তর্ভুক্ত (+১ দিন)</span>
            </label>
          </div>
        </div>

        {/* Results Column */}
        <div className="space-y-4 md:col-span-2">
          {/* Main Hero Result Card */}
          <div className="bg-gradient-to-br from-sky-600 via-teal-600 to-emerald-700 text-white p-6 sm:p-8 rounded-2xl shadow-lg relative overflow-hidden space-y-4">
            <div className="flex items-center justify-between">
              <span className="text-xs font-semibold text-sky-100 uppercase tracking-wider flex items-center gap-1.5">
                <Sparkles className="w-4 h-4 text-sky-200" />
                <span>সঠিক বয়স ফলাফল</span>
              </span>
              <span className="text-xs bg-white/20 px-3 py-1 rounded-full font-bold">
                লাইভ আপডেট
              </span>
            </div>

            <div className="text-3xl sm:text-4xl font-bold font-serif-bn tracking-tight">
              {result.formattedText}
            </div>

            <div className="grid grid-cols-3 gap-3 pt-2 text-center border-t border-white/20">
              <div className="bg-white/10 rounded-xl p-2.5 backdrop-blur-xs">
                <div className="text-2xl font-bold">{toBanglaDigits(result.years)}</div>
                <div className="text-xs text-sky-100">বছর</div>
              </div>
              <div className="bg-white/10 rounded-xl p-2.5 backdrop-blur-xs">
                <div className="text-2xl font-bold">{toBanglaDigits(result.months)}</div>
                <div className="text-xs text-sky-100">মাস</div>
              </div>
              <div className="bg-white/10 rounded-xl p-2.5 backdrop-blur-xs">
                <div className="text-2xl font-bold">{toBanglaDigits(result.days)}</div>
                <div className="text-xs text-sky-100">দিন</div>
              </div>
            </div>
          </div>

          {/* Breakdown Stats Grid */}
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
            {/* Total Days */}
            <div className="bg-white p-4 rounded-xl border border-slate-200/80 shadow-xs text-center space-y-1">
              <span className="text-[11px] text-slate-500 font-semibold block">মোট দিন</span>
              <div className="text-xl font-bold text-slate-800">
                {toBanglaDigits(result.totalDays)}
              </div>
              <span className="text-[10px] text-slate-400">দিন অতিক্রান্ত</span>
            </div>

            {/* Total Weeks */}
            <div className="bg-white p-4 rounded-xl border border-slate-200/80 shadow-xs text-center space-y-1">
              <span className="text-[11px] text-slate-500 font-semibold block">মোট সপ্তাহ</span>
              <div className="text-xl font-bold text-slate-800">
                {toBanglaDigits(result.totalWeeks)}
              </div>
              <span className="text-[10px] text-slate-400">সপ্তাহ</span>
            </div>

            {/* Total Months */}
            <div className="bg-white p-4 rounded-xl border border-slate-200/80 shadow-xs text-center space-y-1">
              <span className="text-[11px] text-slate-500 font-semibold block">মোট মাস</span>
              <div className="text-xl font-bold text-slate-800">
                {toBanglaDigits(result.totalMonths)}
              </div>
              <span className="text-[10px] text-slate-400">মাস</span>
            </div>

            {/* Next Birthday Countdown */}
            <div className="bg-white p-4 rounded-xl border border-slate-200/80 shadow-xs text-center space-y-1">
              <span className="text-[11px] text-pink-600 font-semibold flex items-center justify-center gap-1">
                <Heart className="w-3 h-3 text-pink-500" />
                <span>পরবর্তী জন্মদিন</span>
              </span>
              <div className="text-xl font-bold text-pink-700">
                {toBanglaDigits(result.nextBirthdayDays)}
              </div>
              <span className="text-[10px] text-slate-400">দিন বাকি</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
