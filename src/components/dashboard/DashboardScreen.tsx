import React from 'react';
import {
  Users,
  UserCheck,
  Building2,
  Calendar,
  Sparkles,
  ArrowUpRight,
  TrendingUp,
  FileCheck,
  CheckCircle2,
  Clock,
  Printer,
  ChevronRight,
  HeartHandshake,
  MapPin,
  Phone,
  Scan,
  Award,
  IdCard,
  UserPlus
} from 'lucide-react';
import { useApp } from '../../context/AppContext';
import { toBanglaDigits, formatBanglaDate, getTodayDateStr } from '../../utils/banglaUtils';
import { ToolRoute, ActiveScreen, Student } from '../../types';

interface DashboardScreenProps {
  onOpenAddStudent: () => void;
  onSelectStudent: (student: Student) => void;
}

export const DashboardScreen: React.FC<DashboardScreenProps> = ({
  onOpenAddStudent,
  onSelectStudent
}) => {
  const {
    schoolInfo,
    students,
    stats,
    setActiveScreen,
    setActiveTool,
    attendanceRecords
  } = useApp();

  const handleOpenTool = (tool: ToolRoute) => {
    setActiveScreen('tools_hub');
    setActiveTool(tool);
  };

  // Calculate percentages
  const boyPct = stats.totalStudents > 0 ? Math.round((stats.totalBoys / stats.totalStudents) * 100) : 0;
  const girlPct = stats.totalStudents > 0 ? 100 - boyPct : 0;

  const internalPct = stats.totalStudents > 0 ? Math.round((stats.totalInternal / stats.totalStudents) * 100) : 0;
  const externalPct = stats.totalStudents > 0 ? 100 - internalPct : 0;

  // Recent students
  const recentStudents = [...students].slice(0, 5);

  // Today's attendance calculation
  const todayStr = getTodayDateStr();
  const todayRecords = attendanceRecords.filter(r => r.date === todayStr);
  const totalPresentToday = todayRecords.reduce((acc, r) => acc + r.presentBoys + r.presentGirls, 0);
  const totalEnrolledInRecords = todayRecords.reduce((acc, r) => acc + r.totalBoys + r.totalGirls, 0);
  const attendanceRate = totalEnrolledInRecords > 0 ? Math.round((totalPresentToday / totalEnrolledInRecords) * 100) : 0;

  const standardClasses = [
    'প্রাক-প্রাথমিক ৪+',
    'প্রাক-প্রাথমিক ৫+',
    '১ম শ্রেণি',
    '২য় শ্রেণি',
    '৩য় শ্রেণি',
    '৪র্থ শ্রেণি',
    '৫ম শ্রেণি'
  ];

  return (
    <div className="space-y-6 pb-12">
      {/* School Header Banner */}
      <div className="bg-gradient-to-br from-emerald-800 via-teal-800 to-slate-900 text-white rounded-2xl p-6 sm:p-8 shadow-xl relative overflow-hidden">
        {/* Background decorative pattern */}
        <div className="absolute right-0 top-0 translate-x-12 -translate-y-12 w-64 h-64 bg-emerald-500/10 rounded-full blur-3xl pointer-events-none" />
        <div className="absolute left-1/3 bottom-0 w-80 h-80 bg-teal-400/10 rounded-full blur-3xl pointer-events-none" />

        <div className="relative z-10 flex flex-col md:flex-row md:items-center md:justify-between gap-6">
          <div className="space-y-2 max-w-2xl">
            <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-emerald-700/60 border border-emerald-500/30 text-emerald-200 text-xs font-semibold">
              <span>EIIN: {toBanglaDigits(schoolInfo.eiinCode || '134251')}</span>
              <span>•</span>
              <span>প্রাথমিক ও গণশিক্ষা মন্ত্রণালয়</span>
            </div>
            <h1 className="text-2xl sm:text-3xl font-bold font-serif-bn tracking-tight">
              {schoolInfo.schoolName}
            </h1>
            <p className="text-emerald-100/90 text-sm flex items-center gap-2">
              <MapPin className="w-4 h-4 text-emerald-400 shrink-0" />
              <span>{schoolInfo.address}</span>
            </p>
            <div className="flex flex-wrap gap-4 text-xs text-emerald-200/80 pt-1">
              <span className="flex items-center gap-1.5">
                <Building2 className="w-3.5 h-3.5" />
                প্রধান শিক্ষক: {schoolInfo.headTeacherName}
              </span>
              <span className="flex items-center gap-1.5">
                <Phone className="w-3.5 h-3.5" />
                {toBanglaDigits(schoolInfo.phone)}
              </span>
              <span className="italic">"{schoolInfo.tagline}"</span>
            </div>
          </div>

          <div className="flex flex-wrap gap-3">
            <button
              onClick={onOpenAddStudent}
              className="inline-flex items-center gap-2 px-4 py-2.5 rounded-xl bg-white text-emerald-800 hover:bg-emerald-50 font-semibold text-sm shadow-md transition-colors cursor-pointer"
            >
              <UserPlus className="w-4 h-4" />
              <span>শিক্ষার্থী ভর্তি</span>
            </button>
            <button
              onClick={() => handleOpenTool('attendance_report')}
              className="inline-flex items-center gap-2 px-4 py-2.5 rounded-xl bg-emerald-700/80 hover:bg-emerald-600/90 border border-emerald-500/40 text-white font-medium text-sm transition-colors cursor-pointer"
            >
              <UserCheck className="w-4 h-4" />
              <span>দৈনিক হাজিরা</span>
            </button>
          </div>
        </div>
      </div>

      {/* Primary KPI Metrics Cards */}
      <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3.5">
        {/* Total Students */}
        <div className="bg-white p-4 rounded-xl border border-slate-200/80 shadow-xs hover:border-emerald-300 transition-all">
          <div className="flex items-center justify-between text-slate-500 mb-2">
            <span className="text-xs font-semibold">মোট শিক্ষার্থী</span>
            <div className="w-7 h-7 rounded-lg bg-emerald-50 text-emerald-600 flex items-center justify-center">
              <Users className="w-4 h-4" />
            </div>
          </div>
          <div className="text-2xl font-bold text-slate-800">
            {toBanglaDigits(stats.totalStudents)}
          </div>
          <span className="text-[11px] text-emerald-600 font-medium">নিবন্ধিত শিক্ষার্থী</span>
        </div>

        {/* Total Boys */}
        <div className="bg-white p-4 rounded-xl border border-slate-200/80 shadow-xs hover:border-sky-300 transition-all">
          <div className="flex items-center justify-between text-slate-500 mb-2">
            <span className="text-xs font-semibold">ছাত্র (বালক)</span>
            <div className="w-7 h-7 rounded-lg bg-sky-50 text-sky-600 flex items-center justify-center">
              <span className="font-bold text-xs">♂</span>
            </div>
          </div>
          <div className="text-2xl font-bold text-sky-700">
            {toBanglaDigits(stats.totalBoys)}
          </div>
          <span className="text-[11px] text-slate-500 font-medium">{toBanglaDigits(boyPct)}% শিক্ষার্থী</span>
        </div>

        {/* Total Girls */}
        <div className="bg-white p-4 rounded-xl border border-slate-200/80 shadow-xs hover:border-pink-300 transition-all">
          <div className="flex items-center justify-between text-slate-500 mb-2">
            <span className="text-xs font-semibold">ছাত্রী (বালিকা)</span>
            <div className="w-7 h-7 rounded-lg bg-pink-50 text-pink-600 flex items-center justify-center">
              <span className="font-bold text-xs">♀</span>
            </div>
          </div>
          <div className="text-2xl font-bold text-pink-700">
            {toBanglaDigits(stats.totalGirls)}
          </div>
          <span className="text-[11px] text-slate-500 font-medium">{toBanglaDigits(girlPct)}% শিক্ষার্থী</span>
        </div>

        {/* Internal Village Students */}
        <div className="bg-white p-4 rounded-xl border border-slate-200/80 shadow-xs hover:border-indigo-300 transition-all">
          <div className="flex items-center justify-between text-slate-500 mb-2">
            <span className="text-xs font-semibold">অভ্যন্তরীণ</span>
            <div className="w-7 h-7 rounded-lg bg-indigo-50 text-indigo-600 flex items-center justify-center">
              <Building2 className="w-4 h-4" />
            </div>
          </div>
          <div className="text-2xl font-bold text-indigo-700">
            {toBanglaDigits(stats.totalInternal)}
          </div>
          <span className="text-[11px] text-slate-500 font-medium">{toBanglaDigits(internalPct)}% নিজস্ব এলাকা</span>
        </div>

        {/* External Village Students */}
        <div className="bg-white p-4 rounded-xl border border-slate-200/80 shadow-xs hover:border-amber-300 transition-all">
          <div className="flex items-center justify-between text-slate-500 mb-2">
            <span className="text-xs font-semibold">বহিরাগত</span>
            <div className="w-7 h-7 rounded-lg bg-amber-50 text-amber-600 flex items-center justify-center">
              <MapPin className="w-4 h-4" />
            </div>
          </div>
          <div className="text-2xl font-bold text-amber-700">
            {toBanglaDigits(stats.totalExternal)}
          </div>
          <span className="text-[11px] text-slate-500 font-medium">{toBanglaDigits(externalPct)}% পার্শ্ববর্তী গ্রাম</span>
        </div>

        {/* Special Needs */}
        <div className="bg-white p-4 rounded-xl border border-slate-200/80 shadow-xs hover:border-purple-300 transition-all">
          <div className="flex items-center justify-between text-slate-500 mb-2">
            <span className="text-xs font-semibold">বিশেষ চাহিদা</span>
            <div className="w-7 h-7 rounded-lg bg-purple-50 text-purple-600 flex items-center justify-center">
              <HeartHandshake className="w-4 h-4" />
            </div>
          </div>
          <div className="text-2xl font-bold text-purple-700">
            {toBanglaDigits(stats.totalSpecialNeeds)}
          </div>
          <span className="text-[11px] text-purple-600 font-medium">বিশেষ সুবিধাভোগী</span>
        </div>
      </div>

      {/* Analytics Bars & Quick Attendance Summary */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Gender & Demographics Distribution */}
        <div className="bg-white p-5 rounded-2xl border border-slate-200/80 shadow-xs space-y-5">
          <div className="flex items-center justify-between border-b border-slate-100 pb-3">
            <h3 className="font-bold text-slate-800 text-sm flex items-center gap-2">
              <TrendingUp className="w-4 h-4 text-emerald-600" />
              <span>অনুপাত ও বিন্যাস পরিসংখ্যান</span>
            </h3>
            <span className="text-xs text-slate-400">রিয়েল-টাইম</span>
          </div>

          {/* Gender Ratio Bar */}
          <div className="space-y-2">
            <div className="flex items-center justify-between text-xs font-semibold text-slate-700">
              <span className="flex items-center gap-1 text-sky-600">
                <span className="w-2 h-2 rounded-full bg-sky-500" />
                ছাত্র: {toBanglaDigits(stats.totalBoys)} জন ({toBanglaDigits(boyPct)}%)
              </span>
              <span className="flex items-center gap-1 text-pink-600">
                <span className="w-2 h-2 rounded-full bg-pink-500" />
                ছাত্রী: {toBanglaDigits(stats.totalGirls)} জন ({toBanglaDigits(girlPct)}%)
              </span>
            </div>
            <div className="w-full h-3 rounded-full bg-slate-100 overflow-hidden flex">
              <div
                style={{ width: `${boyPct}%` }}
                className="bg-gradient-to-r from-sky-500 to-sky-400 h-full transition-all duration-500"
              />
              <div
                style={{ width: `${girlPct}%` }}
                className="bg-gradient-to-r from-pink-400 to-pink-500 h-full transition-all duration-500"
              />
            </div>
          </div>

          {/* Category Ratio Bar */}
          <div className="space-y-2">
            <div className="flex items-center justify-between text-xs font-semibold text-slate-700">
              <span className="flex items-center gap-1 text-indigo-600">
                <span className="w-2 h-2 rounded-full bg-indigo-500" />
                অভ্যন্তরীণ: {toBanglaDigits(stats.totalInternal)} জন ({toBanglaDigits(internalPct)}%)
              </span>
              <span className="flex items-center gap-1 text-amber-600">
                <span className="w-2 h-2 rounded-full bg-amber-500" />
                বহিরাগত: {toBanglaDigits(stats.totalExternal)} জন ({toBanglaDigits(externalPct)}%)
              </span>
            </div>
            <div className="w-full h-3 rounded-full bg-slate-100 overflow-hidden flex">
              <div
                style={{ width: `${internalPct}%` }}
                className="bg-indigo-500 h-full transition-all duration-500"
              />
              <div
                style={{ width: `${externalPct}%` }}
                className="bg-amber-400 h-full transition-all duration-500"
              />
            </div>
          </div>

          {/* Internal Villages Tag List */}
          <div className="pt-2 border-t border-slate-100">
            <span className="text-xs font-semibold text-slate-500 block mb-1.5">
              বিদ্যালয় ক্যাচমেন্টভুক্ত অভ্যন্তরীণ গ্রামসমূহ:
            </span>
            <div className="flex flex-wrap gap-1.5">
              {schoolInfo.internalVillages.split(',').map((v, i) => (
                <span
                  key={i}
                  className="px-2 py-0.5 rounded-md bg-slate-100 text-slate-700 text-xs border border-slate-200"
                >
                  {v.trim()}
                </span>
              ))}
            </div>
          </div>
        </div>

        {/* Class-wise Enrolment Breakdown */}
        <div className="bg-white p-5 rounded-2xl border border-slate-200/80 shadow-xs space-y-4 lg:col-span-2">
          <div className="flex items-center justify-between border-b border-slate-100 pb-3">
            <h3 className="font-bold text-slate-800 text-sm flex items-center gap-2">
              <Users className="w-4 h-4 text-teal-600" />
              <span>শ্রেণিভিত্তিক শিক্ষার্থী সংখ্যা</span>
            </h3>
            <button
              onClick={() => setActiveScreen('students')}
              className="text-xs text-emerald-600 hover:text-emerald-700 font-semibold flex items-center gap-1 cursor-pointer"
            >
              <span>সকল দেখুন</span>
              <ChevronRight className="w-3.5 h-3.5" />
            </button>
          </div>

          <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-2.5">
            {standardClasses.map(clsName => {
              const cData = stats.classCounts[clsName] || { total: 0, boys: 0, girls: 0 };
              return (
                <div
                  key={clsName}
                  onClick={() => {
                    setActiveScreen('students');
                  }}
                  className="p-3 rounded-xl bg-slate-50 hover:bg-emerald-50/50 border border-slate-200/60 hover:border-emerald-300 transition-all cursor-pointer group"
                >
                  <div className="font-semibold text-slate-800 text-xs mb-1 group-hover:text-emerald-700">
                    {clsName}
                  </div>
                  <div className="text-xl font-bold text-slate-900">
                    {toBanglaDigits(cData.total)}
                    <span className="text-xs font-normal text-slate-500 ml-1">জন</span>
                  </div>
                  <div className="flex items-center justify-between text-[11px] text-slate-500 mt-1 pt-1 border-t border-slate-200/60">
                    <span className="text-sky-600">ছাত্র: {toBanglaDigits(cData.boys)}</span>
                    <span className="text-pink-600">ছাত্রী: {toBanglaDigits(cData.girls)}</span>
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      </div>

      {/* Quick Tools Hub Shortcuts */}
      <div className="bg-white p-5 rounded-2xl border border-slate-200/80 shadow-xs">
        <div className="flex items-center justify-between mb-4">
          <div>
            <h3 className="font-bold text-slate-800 text-base flex items-center gap-2">
              <Sparkles className="w-4 h-4 text-emerald-600" />
              <span>স্মার্ট টুলস ও মূল্যায়ন হাব</span>
            </h3>
            <p className="text-xs text-slate-500">
              দৈনিক হাজিরা, পরীক্ষা ও সনদ তৈরির সহজ ও স্বয়ংক্রিয় সমাধান
            </p>
          </div>
          <button
            onClick={() => setActiveScreen('tools_hub')}
            className="text-xs text-emerald-600 hover:text-emerald-700 font-semibold flex items-center gap-1 cursor-pointer"
          >
            <span>টুলস হাব খুলুন</span>
            <ArrowUpRight className="w-4 h-4" />
          </button>
        </div>

        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3">
          {/* Tool 1: Attendance */}
          <button
            onClick={() => handleOpenTool('attendance_report')}
            className="p-3.5 rounded-xl border border-slate-200 hover:border-emerald-400 hover:bg-emerald-50/40 text-left transition-all group cursor-pointer"
          >
            <div className="w-9 h-9 rounded-lg bg-emerald-100 text-emerald-700 flex items-center justify-center mb-2.5 group-hover:scale-105 transition-transform">
              <UserCheck className="w-5 h-5" />
            </div>
            <div className="font-semibold text-xs text-slate-800 group-hover:text-emerald-700">
              হাজিরা ও রিপোর্ট
            </div>
            <div className="text-[11px] text-slate-500 mt-0.5">মাসিক হাজিরা খাতা</div>
          </button>

          {/* Tool 2: Age Calc */}
          <button
            onClick={() => handleOpenTool('age_calculator')}
            className="p-3.5 rounded-xl border border-slate-200 hover:border-sky-400 hover:bg-sky-50/40 text-left transition-all group cursor-pointer"
          >
            <div className="w-9 h-9 rounded-lg bg-sky-100 text-sky-700 flex items-center justify-center mb-2.5 group-hover:scale-105 transition-transform">
              <Calendar className="w-5 h-5" />
            </div>
            <div className="font-semibold text-xs text-slate-800 group-hover:text-sky-700">
              বয়স ক্যালকুলেটর
            </div>
            <div className="text-[11px] text-slate-500 mt-0.5">দিন, মাস ও বছর গণনা</div>
          </button>

          {/* Tool 3: Admit Card */}
          <button
            onClick={() => handleOpenTool('admit_card_maker')}
            className="p-3.5 rounded-xl border border-slate-200 hover:border-indigo-400 hover:bg-indigo-50/40 text-left transition-all group cursor-pointer"
          >
            <div className="w-9 h-9 rounded-lg bg-indigo-100 text-indigo-700 flex items-center justify-center mb-2.5 group-hover:scale-105 transition-transform">
              <IdCard className="w-5 h-5" />
            </div>
            <div className="font-semibold text-xs text-slate-800 group-hover:text-indigo-700">
              প্রবেশপত্র ও রুটিন
            </div>
            <div className="text-[11px] text-slate-500 mt-0.5">এডমিট কার্ড জেনারেটর</div>
          </button>

          {/* Tool 4: Seat Plan */}
          <button
            onClick={() => handleOpenTool('seat_plan_maker')}
            className="p-3.5 rounded-xl border border-slate-200 hover:border-amber-400 hover:bg-amber-50/40 text-left transition-all group cursor-pointer"
          >
            <div className="w-9 h-9 rounded-lg bg-amber-100 text-amber-700 flex items-center justify-center mb-2.5 group-hover:scale-105 transition-transform">
              <Building2 className="w-5 h-5" />
            </div>
            <div className="font-semibold text-xs text-slate-800 group-hover:text-amber-700">
              সিট প্ল্যান মেকার
            </div>
            <div className="text-[11px] text-slate-500 mt-0.5">আসন বিন্যাস ও স্টিকার</div>
          </button>

          {/* Tool 5: Certificate */}
          <button
            onClick={() => handleOpenTool('certificate_maker')}
            className="p-3.5 rounded-xl border border-slate-200 hover:border-rose-400 hover:bg-rose-50/40 text-left transition-all group cursor-pointer"
          >
            <div className="w-9 h-9 rounded-lg bg-rose-100 text-rose-700 flex items-center justify-center mb-2.5 group-hover:scale-105 transition-transform">
              <Award className="w-5 h-5" />
            </div>
            <div className="font-semibold text-xs text-slate-800 group-hover:text-rose-700">
              প্রত্যয়ন ও প্রশংসা
            </div>
            <div className="text-[11px] text-slate-500 mt-0.5">অফিস কপি সহ সনদ</div>
          </button>

          {/* Tool 6: Document Scanner */}
          <button
            onClick={() => handleOpenTool('doc_scanner')}
            className="p-3.5 rounded-xl border border-slate-200 hover:border-teal-400 hover:bg-teal-50/40 text-left transition-all group cursor-pointer"
          >
            <div className="w-9 h-9 rounded-lg bg-teal-100 text-teal-700 flex items-center justify-center mb-2.5 group-hover:scale-105 transition-transform">
              <Scan className="w-5 h-5" />
            </div>
            <div className="font-semibold text-xs text-slate-800 group-hover:text-teal-700">
              ডকুমেন্ট স্ক্যানার
            </div>
            <div className="text-[11px] text-slate-500 mt-0.5">ছবি ও ফাইল সংরক্ষণ</div>
          </button>
        </div>
      </div>

      {/* Recent Students & Today's Attendance Feed */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Recent Registered Students */}
        <div className="bg-white p-5 rounded-2xl border border-slate-200/80 shadow-xs space-y-4">
          <div className="flex items-center justify-between border-b border-slate-100 pb-3">
            <h3 className="font-bold text-slate-800 text-sm flex items-center gap-2">
              <Users className="w-4 h-4 text-emerald-600" />
              <span>সাম্প্রতিক নিবন্ধিত শিক্ষার্থী</span>
            </h3>
            <button
              onClick={() => setActiveScreen('students')}
              className="text-xs text-emerald-600 hover:text-emerald-700 font-semibold"
            >
              সম্পূর্ণ তালিকা
            </button>
          </div>

          <div className="divide-y divide-slate-100">
            {recentStudents.map(student => (
              <div
                key={student.id}
                onClick={() => onSelectStudent(student)}
                className="py-2.5 flex items-center justify-between hover:bg-slate-50 px-2 rounded-lg transition-colors cursor-pointer"
              >
                <div className="flex items-center gap-3">
                  <div className="w-9 h-9 rounded-full bg-slate-100 border border-slate-200 flex items-center justify-center font-bold text-xs text-slate-700 shrink-0">
                    {student.gender === 'ছাত্রী' || student.gender === 'বালিকা' ? '👩' : '👦'}
                  </div>
                  <div>
                    <div className="font-semibold text-xs text-slate-800">{student.name}</div>
                    <div className="text-[11px] text-slate-500">
                      {student.studentClass} • রোল {toBanglaDigits(student.rollNumber)} • {student.village}
                    </div>
                  </div>
                </div>
                <div className="text-right">
                  <span
                    className={`text-[11px] px-2 py-0.5 rounded-full font-medium ${
                      student.status === 'Current'
                        ? 'bg-emerald-50 text-emerald-700 border border-emerald-200/60'
                        : 'bg-slate-100 text-slate-600'
                    }`}
                  >
                    {student.status === 'Current' ? 'নিয়মিত' : student.status}
                  </span>
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* Daily Attendance Overview */}
        <div className="bg-white p-5 rounded-2xl border border-slate-200/80 shadow-xs space-y-4">
          <div className="flex items-center justify-between border-b border-slate-100 pb-3">
            <div className="flex items-center gap-2">
              <Clock className="w-4 h-4 text-emerald-600" />
              <h3 className="font-bold text-slate-800 text-sm">
                আজকের হাজিরা সামারি ({formatBanglaDate(todayStr)})
              </h3>
            </div>
            <button
              onClick={() => handleOpenTool('attendance_report')}
              className="text-xs text-emerald-600 hover:text-emerald-700 font-semibold"
            >
              হাজিরা ইনপুট দিন
            </button>
          </div>

          {todayRecords.length === 0 ? (
            <div className="py-8 text-center space-y-2">
              <UserCheck className="w-10 h-10 text-slate-300 mx-auto" />
              <p className="text-xs text-slate-500">আজকের কোন হাজিরা রেকর্ড এখনো এন্ট্রি করা হয়নি</p>
              <button
                onClick={() => handleOpenTool('attendance_report')}
                className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs rounded-lg bg-emerald-50 text-emerald-700 font-medium hover:bg-emerald-100 transition-colors cursor-pointer"
              >
                <Plus className="w-3.5 h-3.5" />
                <span>আজকের হাজিরা গ্রহণ করুন</span>
              </button>
            </div>
          ) : (
            <div className="space-y-3">
              <div className="p-3.5 rounded-xl bg-emerald-50/60 border border-emerald-200/60 flex items-center justify-between">
                <div>
                  <span className="text-xs text-emerald-800 font-semibold">আজকের গড় উপস্থিতি</span>
                  <div className="text-2xl font-bold text-emerald-900">
                    {toBanglaDigits(attendanceRate)}%
                  </div>
                </div>
                <div className="text-right text-xs text-emerald-700">
                  <div>উপস্থিত: {toBanglaDigits(totalPresentToday)} জন</div>
                  <div>মোট তালিকাভুক্ত: {toBanglaDigits(totalEnrolledInRecords)} জন</div>
                </div>
              </div>

              <div className="divide-y divide-slate-100 max-h-48 overflow-y-auto">
                {todayRecords.map(r => (
                  <div key={r.id} className="py-2 flex items-center justify-between text-xs">
                    <span className="font-semibold text-slate-700">{r.className}</span>
                    <span className="text-slate-600">
                      উপস্থিত: {toBanglaDigits(r.presentBoys + r.presentGirls)} / {toBanglaDigits(r.totalBoys + r.totalGirls)}
                    </span>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
