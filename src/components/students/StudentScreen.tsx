import React, { useState, useMemo } from 'react';
import {
  Users,
  Search,
  Filter,
  Plus,
  Trash2,
  Edit2,
  Eye,
  IdCard,
  Download,
  Upload,
  Printer,
  CheckSquare,
  Square,
  Sparkles,
  LayoutGrid,
  List,
  ChevronDown,
  X,
  Phone,
  MapPin,
  Calendar,
  AlertCircle
} from 'lucide-react';
import { useApp } from '../../context/AppContext';
import { Student } from '../../types';
import { toBanglaDigits, formatBanglaDate } from '../../utils/banglaUtils';
import { FormulaEvaluator } from '../../utils/formulaEvaluator';
import { StudentFormModal } from './StudentFormModal';
import { StudentDetailModal } from './StudentDetailModal';
import { StudentIdCardModal } from './StudentIdCardModal';
import { StudentBulkActionsModal } from './StudentBulkActionsModal';
import { StudentImportExportModal } from './StudentImportExportModal';

interface StudentScreenProps {
  onOpenAddStudent: () => void;
  selectedStudentFromDash?: Student | null;
}

export const StudentScreen: React.FC<StudentScreenProps> = ({
  onOpenAddStudent,
  selectedStudentFromDash
}) => {
  const {
    students,
    filteredStudents,
    searchQuery,
    setSearchQuery,
    filterClass,
    setFilterClass,
    filterGender,
    setFilterGender,
    filterStatus,
    setFilterStatus,
    filterVillage,
    setFilterVillage,
    filterSpecialNeeds,
    setFilterSpecialNeeds,
    filterCategory,
    setFilterCategory,
    resetFilters,
    deleteStudent,
    schoolInfo,
    customFields,
    formulaRules
  } = useApp();

  const [viewMode, setViewMode] = useState<'table' | 'grid'>('table');
  const [isSelectionMode, setIsSelectionMode] = useState(false);
  const [selectedStudentIds, setSelectedStudentIds] = useState<string[]>([]);

  // Modals state
  const [editingStudent, setEditingStudent] = useState<Student | null>(null);
  const [isFormOpen, setIsFormOpen] = useState(false);
  const [viewingStudent, setViewingStudent] = useState<Student | null>(selectedStudentFromDash || null);
  const [idCardStudent, setIdCardStudent] = useState<Student | null>(null);
  const [showBulkModal, setShowBulkModal] = useState(false);
  const [showImportExport, setShowImportExport] = useState(false);
  const [studentToDelete, setStudentToDelete] = useState<Student | null>(null);

  const standardClasses = [
    'ALL',
    'প্রাক-প্রাথমিক ৪+',
    'প্রাক-প্রাথমিক ৫+',
    '১ম শ্রেণি',
    '২য় শ্রেণি',
    '৩য় শ্রেণি',
    '৪র্থ শ্রেণি',
    '৫ম শ্রেণি'
  ];

  // Distinct villages
  const villages = useMemo(() => {
    const set = new Set(students.map(s => s.village).filter(Boolean));
    return ['ALL', ...Array.from(set)];
  }, [students]);

  // Active filters count
  const activeFiltersCount = [
    filterClass !== 'ALL',
    filterGender !== 'ALL',
    filterStatus !== 'ALL',
    filterVillage !== 'ALL',
    filterSpecialNeeds !== 'ALL',
    filterCategory !== 'ALL'
  ].filter(Boolean).length;

  const toggleSelectAll = () => {
    if (selectedStudentIds.length === filteredStudents.length) {
      setSelectedStudentIds([]);
    } else {
      setSelectedStudentIds(filteredStudents.map(s => s.id));
    }
  };

  const toggleSelectStudent = (id: string) => {
    setSelectedStudentIds(prev =>
      prev.includes(id) ? prev.filter(item => item !== id) : [...prev, id]
    );
  };

  const handlePrintList = () => {
    window.print();
  };

  return (
    <div className="space-y-5 pb-16">
      {/* Top Header & Quick Stats */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 bg-white p-5 rounded-2xl border border-slate-200/80 shadow-xs">
        <div>
          <div className="flex items-center gap-2">
            <h1 className="text-xl font-bold text-slate-900 font-serif-bn">শিক্ষার্থী তালিকা</h1>
            <span className="text-xs px-2.5 py-0.5 rounded-full bg-emerald-100 text-emerald-800 font-bold">
              {toBanglaDigits(filteredStudents.length)} জন প্রদর্শিত
            </span>
          </div>
          <p className="text-xs text-slate-500 mt-0.5">
            শিক্ষার্থীদের পূর্ণাঙ্গ প্রোফাইল, তথ্য অনুসন্ধান, আইডি কার্ড ও ব্যাচ অপারেশন
          </p>
        </div>

        {/* Header Action Buttons */}
        <div className="flex flex-wrap items-center gap-2">
          <button
            onClick={() => {
              setIsSelectionMode(!isSelectionMode);
              if (isSelectionMode) setSelectedStudentIds([]);
            }}
            className={`inline-flex items-center gap-1.5 px-3 py-2 rounded-lg text-xs font-semibold border transition-all cursor-pointer ${
              isSelectionMode
                ? 'bg-slate-800 text-white border-slate-800'
                : 'border-slate-300 bg-white hover:bg-slate-50 text-slate-700'
            }`}
          >
            <CheckSquare className="w-4 h-4" />
            <span>{isSelectionMode ? 'সিলেকশন বন্ধ' : 'সিলেকশন মোড'}</span>
          </button>

          <button
            onClick={() => setShowImportExport(true)}
            className="inline-flex items-center gap-1.5 px-3 py-2 rounded-lg border border-slate-300 bg-white hover:bg-slate-50 text-slate-700 text-xs font-semibold transition-all cursor-pointer"
          >
            <Upload className="w-4 h-4" />
            <span>ইম্পোর্ট / এক্সপোর্ট</span>
          </button>

          <button
            onClick={handlePrintList}
            className="inline-flex items-center gap-1.5 px-3 py-2 rounded-lg border border-slate-300 bg-white hover:bg-slate-50 text-slate-700 text-xs font-semibold transition-all cursor-pointer"
          >
            <Printer className="w-4 h-4" />
            <span>প্রিন্ট</span>
          </button>

          <button
            onClick={() => {
              setEditingStudent(null);
              setIsFormOpen(true);
            }}
            className="inline-flex items-center gap-1.5 px-4 py-2 rounded-lg bg-emerald-600 hover:bg-emerald-700 text-white text-xs font-bold shadow-md shadow-emerald-600/20 transition-all cursor-pointer"
          >
            <Plus className="w-4 h-4" />
            <span>নতুন শিক্ষার্থী ভর্তি</span>
          </button>
        </div>
      </div>

      {/* Filter and Search Bar */}
      <div className="bg-white p-4 rounded-2xl border border-slate-200/80 shadow-xs space-y-3">
        <div className="flex flex-col md:flex-row items-center gap-3">
          {/* Search Input */}
          <div className="relative flex-1 w-full">
            <Search className="w-4 h-4 absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400" />
            <input
              type="text"
              value={searchQuery}
              onChange={e => setSearchQuery(e.target.value)}
              placeholder="নাম, রোল, মোবাইল, জন্ম নিবন্ধন বা পিতা/মাতার নাম..."
              className="w-full pl-9 pr-8 py-2 text-xs rounded-xl bg-slate-50 border border-slate-200 focus:bg-white focus:border-emerald-500 focus:ring-2 focus:ring-emerald-500/20 outline-hidden transition-all"
            />
            {searchQuery && (
              <button
                onClick={() => setSearchQuery('')}
                className="absolute right-2.5 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600 text-xs"
              >
                <X className="w-4 h-4" />
              </button>
            )}
          </div>

          {/* View Mode Toggle */}
          <div className="flex items-center gap-1 bg-slate-100 p-1 rounded-xl border border-slate-200 shrink-0 self-end md:self-center">
            <button
              onClick={() => setViewMode('table')}
              className={`p-1.5 rounded-lg text-xs font-semibold flex items-center gap-1 transition-all cursor-pointer ${
                viewMode === 'table' ? 'bg-white text-emerald-700 shadow-xs' : 'text-slate-600 hover:text-slate-900'
              }`}
            >
              <List className="w-4 h-4" />
              <span className="hidden sm:inline">সারণি</span>
            </button>
            <button
              onClick={() => setViewMode('grid')}
              className={`p-1.5 rounded-lg text-xs font-semibold flex items-center gap-1 transition-all cursor-pointer ${
                viewMode === 'grid' ? 'bg-white text-emerald-700 shadow-xs' : 'text-slate-600 hover:text-slate-900'
              }`}
            >
              <LayoutGrid className="w-4 h-4" />
              <span className="hidden sm:inline">কার্ড</span>
            </button>
          </div>
        </div>

        {/* Filter Dropdowns Row */}
        <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-6 gap-2 pt-2 border-t border-slate-100 text-xs">
          {/* Class Filter */}
          <div>
            <label className="block text-[11px] font-semibold text-slate-500 mb-1">শ্রেণি</label>
            <select
              value={filterClass}
              onChange={e => setFilterClass(e.target.value)}
              className="w-full px-2 py-1.5 rounded-lg border border-slate-200 bg-slate-50 text-slate-800 text-xs outline-hidden focus:border-emerald-500"
            >
              {standardClasses.map(c => (
                <option key={c} value={c}>
                  {c === 'ALL' ? 'সকল শ্রেণি' : c}
                </option>
              ))}
            </select>
          </div>

          {/* Gender Filter */}
          <div>
            <label className="block text-[11px] font-semibold text-slate-500 mb-1">লিঙ্গ</label>
            <select
              value={filterGender}
              onChange={e => setFilterGender(e.target.value)}
              className="w-full px-2 py-1.5 rounded-lg border border-slate-200 bg-slate-50 text-slate-800 text-xs outline-hidden focus:border-emerald-500"
            >
              <option value="ALL">সকল</option>
              <option value="MALE">ছাত্র (বালক)</option>
              <option value="FEMALE">ছাত্রী (বালিকা)</option>
            </select>
          </div>

          {/* Category Filter */}
          <div>
            <label className="block text-[11px] font-semibold text-slate-500 mb-1">ক্যাটাগরি</label>
            <select
              value={filterCategory}
              onChange={e => setFilterCategory(e.target.value)}
              className="w-full px-2 py-1.5 rounded-lg border border-slate-200 bg-slate-50 text-slate-800 text-xs outline-hidden focus:border-emerald-500"
            >
              <option value="ALL">সকল</option>
              <option value="অভ্যন্তরীণ">অভ্যন্তরীণ</option>
              <option value="বহিরাগত">বহিরাগত</option>
            </select>
          </div>

          {/* Village Filter */}
          <div>
            <label className="block text-[11px] font-semibold text-slate-500 mb-1">গ্রাম</label>
            <select
              value={filterVillage}
              onChange={e => setFilterVillage(e.target.value)}
              className="w-full px-2 py-1.5 rounded-lg border border-slate-200 bg-slate-50 text-slate-800 text-xs outline-hidden focus:border-emerald-500"
            >
              {villages.map(v => (
                <option key={v} value={v}>
                  {v === 'ALL' ? 'সকল গ্রাম' : v}
                </option>
              ))}
            </select>
          </div>

          {/* Special Needs */}
          <div>
            <label className="block text-[11px] font-semibold text-slate-500 mb-1">বিশেষ চাহিদা</label>
            <select
              value={filterSpecialNeeds}
              onChange={e => setFilterSpecialNeeds(e.target.value)}
              className="w-full px-2 py-1.5 rounded-lg border border-slate-200 bg-slate-50 text-slate-800 text-xs outline-hidden focus:border-emerald-500"
            >
              <option value="ALL">সকল</option>
              <option value="YES">হ্যাঁ (বিশেষ সুবিধা)</option>
              <option value="NO">না</option>
            </select>
          </div>

          {/* Status Filter */}
          <div>
            <label className="block text-[11px] font-semibold text-slate-500 mb-1">স্ট্যাটাস</label>
            <select
              value={filterStatus}
              onChange={e => setFilterStatus(e.target.value)}
              className="w-full px-2 py-1.5 rounded-lg border border-slate-200 bg-slate-50 text-slate-800 text-xs outline-hidden focus:border-emerald-500"
            >
              <option value="ALL">সকল স্ট্যাটাস</option>
              <option value="Current">নিয়মিত (Current)</option>
              <option value="Former">প্রাক্তন (Former)</option>
              <option value="Transferred">ছাড়পত্রপ্রাপ্ত</option>
              <option value="Inactive">নিষ্ক্রিয়</option>
            </select>
          </div>
        </div>

        {activeFiltersCount > 0 && (
          <div className="flex items-center justify-between pt-1">
            <span className="text-[11px] text-emerald-700 font-medium">
              {toBanglaDigits(activeFiltersCount)} টি ফিল্টার সক্রিয় রয়েছে
            </span>
            <button
              onClick={resetFilters}
              className="text-[11px] text-rose-600 hover:text-rose-700 font-semibold cursor-pointer"
            >
              সকল ফিল্টার মুছুন
            </button>
          </div>
        )}
      </div>

      {/* Multi-Selection Bulk Action Bar */}
      {isSelectionMode && (
        <div className="bg-slate-900 text-white p-3.5 rounded-xl flex items-center justify-between shadow-lg animate-in slide-in-from-top-2 duration-200">
          <div className="flex items-center gap-3">
            <button
              onClick={toggleSelectAll}
              className="flex items-center gap-1.5 text-xs font-semibold bg-slate-800 hover:bg-slate-700 px-3 py-1.5 rounded-lg transition-colors cursor-pointer"
            >
              {selectedStudentIds.length === filteredStudents.length ? (
                <CheckSquare className="w-4 h-4 text-emerald-400" />
              ) : (
                <Square className="w-4 h-4 text-slate-400" />
              )}
              <span>সব নির্বাচন ({toBanglaDigits(selectedStudentIds.length)} জন)</span>
            </button>
          </div>

          <div className="flex items-center gap-2">
            <button
              disabled={selectedStudentIds.length === 0}
              onClick={() => setShowBulkModal(true)}
              className="px-3.5 py-1.5 rounded-lg bg-emerald-600 hover:bg-emerald-700 disabled:opacity-40 text-xs font-bold transition-colors cursor-pointer"
            >
              একত্রে পরিবর্তন / মুছুন
            </button>
          </div>
        </div>
      )}

      {/* Main Student Data View */}
      {filteredStudents.length === 0 ? (
        <div className="bg-white p-12 rounded-2xl border border-slate-200 text-center space-y-3">
          <Users className="w-12 h-12 text-slate-300 mx-auto" />
          <h3 className="text-base font-bold text-slate-700">কোন শিক্ষার্থী পাওয়া যায়নি</h3>
          <p className="text-xs text-slate-500 max-w-sm mx-auto">
            আপনার অনুসন্ধান অথবা ফিল্টারের সাথে মিলে এমন কোন শিক্ষার্থীর রেকর্ড পাওয়া যায়নি।
          </p>
          <button
            onClick={resetFilters}
            className="px-4 py-2 text-xs font-semibold rounded-lg bg-emerald-50 text-emerald-700 hover:bg-emerald-100 transition-colors"
          >
            ফিল্টার রিসেট করুন
          </button>
        </div>
      ) : viewMode === 'table' ? (
        /* TABLE VIEW */
        <div className="bg-white rounded-2xl border border-slate-200/80 shadow-xs overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="bg-slate-50 text-slate-600 font-bold border-b border-slate-200 uppercase text-[11px] tracking-wider">
                <tr>
                  {isSelectionMode && (
                    <th className="p-3.5 w-10 text-center">
                      <input
                        type="checkbox"
                        checked={selectedStudentIds.length === filteredStudents.length && filteredStudents.length > 0}
                        onChange={toggleSelectAll}
                        className="w-4 h-4 text-emerald-600 rounded-sm"
                      />
                    </th>
                  )}
                  <th className="p-3.5">রোল</th>
                  <th className="p-3.5">শিক্ষার্থী</th>
                  <th className="p-3.5">শ্রেণি ও শাখা</th>
                  <th className="p-3.5">গ্রাম / এলাকা</th>
                  <th className="p-3.5">ক্যাটাগরি</th>
                  <th className="p-3.5">মোবাইল</th>
                  <th className="p-3.5 text-right no-print">কার্যক্রম</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {filteredStudents.map(student => {
                  const isSelected = selectedStudentIds.includes(student.id);
                  const category = FormulaEvaluator.getStudentCategory(student, schoolInfo.internalVillages);

                  return (
                    <tr
                      key={student.id}
                      className={`hover:bg-slate-50/80 transition-colors ${
                        isSelected ? 'bg-emerald-50/40' : ''
                      }`}
                    >
                      {isSelectionMode && (
                        <td className="p-3.5 text-center">
                          <input
                            type="checkbox"
                            checked={isSelected}
                            onChange={() => toggleSelectStudent(student.id)}
                            className="w-4 h-4 text-emerald-600 rounded-sm"
                          />
                        </td>
                      )}

                      <td className="p-3.5 font-bold text-slate-800 text-sm">
                        {toBanglaDigits(student.rollNumber)}
                      </td>

                      <td className="p-3.5">
                        <div
                          onClick={() => setViewingStudent(student)}
                          className="flex items-center gap-2.5 cursor-pointer group"
                        >
                          <div className="w-8 h-8 rounded-full bg-slate-100 border border-slate-200 flex items-center justify-center font-bold text-xs shrink-0 overflow-hidden">
                            {student.photoUri ? (
                              <img src={student.photoUri} alt="" className="w-full h-full object-cover" />
                            ) : (
                              <span>{student.gender === 'ছাত্রী' || student.gender === 'বালিকা' ? '👩' : '👦'}</span>
                            )}
                          </div>
                          <div>
                            <div className="font-bold text-slate-900 group-hover:text-emerald-700 transition-colors">
                              {student.name}
                            </div>
                            <div className="text-[11px] text-slate-400">
                              পিতা: {student.fatherName || '—'}
                            </div>
                          </div>
                        </div>
                      </td>

                      <td className="p-3.5 font-medium text-slate-700">
                        <div>{student.studentClass}</div>
                        <div className="text-[10px] text-slate-400">শাখা: {student.section || '—'}</div>
                      </td>

                      <td className="p-3.5 text-slate-600">{student.village || '—'}</td>

                      <td className="p-3.5">
                        <span
                          className={`inline-block px-2 py-0.5 rounded-full text-[10px] font-bold ${
                            category === 'অভ্যন্তরীণ'
                              ? 'bg-emerald-50 text-emerald-700 border border-emerald-200'
                              : 'bg-amber-50 text-amber-700 border border-amber-200'
                          }`}
                        >
                          {category}
                        </span>
                      </td>

                      <td className="p-3.5 font-mono text-slate-600">
                        {toBanglaDigits(student.mobile || student.parentContact || '—')}
                      </td>

                      <td className="p-3.5 text-right no-print">
                        <div className="inline-flex items-center gap-1">
                          <button
                            onClick={() => setViewingStudent(student)}
                            title="পূর্ণাঙ্গ প্রোফাইল"
                            className="p-1.5 rounded-lg text-slate-500 hover:text-emerald-700 hover:bg-slate-100 transition-colors cursor-pointer"
                          >
                            <Eye className="w-4 h-4" />
                          </button>
                          <button
                            onClick={() => setIdCardStudent(student)}
                            title="আইডি কার্ড"
                            className="p-1.5 rounded-lg text-slate-500 hover:text-indigo-700 hover:bg-slate-100 transition-colors cursor-pointer"
                          >
                            <IdCard className="w-4 h-4" />
                          </button>
                          <button
                            onClick={() => {
                              setEditingStudent(student);
                              setIsFormOpen(true);
                            }}
                            title="সম্পাদনা"
                            className="p-1.5 rounded-lg text-slate-500 hover:text-blue-700 hover:bg-slate-100 transition-colors cursor-pointer"
                          >
                            <Edit2 className="w-4 h-4" />
                          </button>
                          <button
                            onClick={() => setStudentToDelete(student)}
                            title="মুছুন"
                            className="p-1.5 rounded-lg text-slate-500 hover:text-rose-700 hover:bg-slate-100 transition-colors cursor-pointer"
                          >
                            <Trash2 className="w-4 h-4" />
                          </button>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      ) : (
        /* GRID CARDS VIEW */
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {filteredStudents.map(student => {
            const isSelected = selectedStudentIds.includes(student.id);
            const category = FormulaEvaluator.getStudentCategory(student, schoolInfo.internalVillages);

            return (
              <div
                key={student.id}
                className={`bg-white rounded-2xl border p-4 shadow-xs space-y-3 relative hover:border-emerald-300 transition-all ${
                  isSelected ? 'border-emerald-500 bg-emerald-50/30' : 'border-slate-200/80'
                }`}
              >
                {isSelectionMode && (
                  <div className="absolute top-3 right-3">
                    <input
                      type="checkbox"
                      checked={isSelected}
                      onChange={() => toggleSelectStudent(student.id)}
                      className="w-4 h-4 text-emerald-600 rounded-sm"
                    />
                  </div>
                )}

                <div className="flex items-start gap-3">
                  <div className="w-12 h-12 rounded-xl bg-slate-100 border border-slate-200 flex items-center justify-center font-bold text-lg overflow-hidden shrink-0">
                    {student.photoUri ? (
                      <img src={student.photoUri} alt="" className="w-full h-full object-cover" />
                    ) : (
                      <span>{student.gender === 'ছাত্রী' || student.gender === 'বালিকা' ? '👩' : '👦'}</span>
                    )}
                  </div>
                  <div className="flex-1 min-w-0 pr-6">
                    <h4
                      onClick={() => setViewingStudent(student)}
                      className="font-bold text-sm text-slate-900 hover:text-emerald-700 cursor-pointer truncate"
                    >
                      {student.name}
                    </h4>
                    <div className="flex items-center gap-1.5 text-xs text-slate-500 mt-0.5">
                      <span className="font-semibold text-emerald-700">{student.studentClass}</span>
                      <span>•</span>
                      <span>রোল: {toBanglaDigits(student.rollNumber)}</span>
                    </div>
                  </div>
                </div>

                <div className="grid grid-cols-2 gap-2 text-[11px] p-2.5 rounded-xl bg-slate-50 border border-slate-200/60">
                  <div>
                    <span className="text-slate-400 block">ক্যাটাগরি</span>
                    <span className="font-bold text-slate-700">{category}</span>
                  </div>
                  <div>
                    <span className="text-slate-400 block">গ্রাম</span>
                    <span className="font-semibold text-slate-700 truncate block">{student.village || '—'}</span>
                  </div>
                  <div>
                    <span className="text-slate-400 block">পিতা</span>
                    <span className="font-semibold text-slate-700 truncate block">{student.fatherName || '—'}</span>
                  </div>
                  <div>
                    <span className="text-slate-400 block">মোবাইল</span>
                    <span className="font-mono text-slate-700 truncate block">
                      {toBanglaDigits(student.mobile || student.parentContact || '—')}
                    </span>
                  </div>
                </div>

                <div className="flex items-center justify-between pt-1 border-t border-slate-100 no-print">
                  <span
                    className={`text-[10px] px-2 py-0.5 rounded-full font-bold ${
                      student.status === 'Current' ? 'bg-emerald-50 text-emerald-700' : 'bg-slate-100 text-slate-600'
                    }`}
                  >
                    {student.status === 'Current' ? 'নিয়মিত' : student.status}
                  </span>

                  <div className="flex items-center gap-1">
                    <button
                      onClick={() => setViewingStudent(student)}
                      title="ভিউ প্রোফাইল"
                      className="p-1 rounded-md text-slate-500 hover:text-emerald-700 hover:bg-slate-100"
                    >
                      <Eye className="w-3.5 h-3.5" />
                    </button>
                    <button
                      onClick={() => setIdCardStudent(student)}
                      title="আইডি কার্ড"
                      className="p-1 rounded-md text-slate-500 hover:text-indigo-700 hover:bg-slate-100"
                    >
                      <IdCard className="w-3.5 h-3.5" />
                    </button>
                    <button
                      onClick={() => {
                        setEditingStudent(student);
                        setIsFormOpen(true);
                      }}
                      title="সম্পাদনা"
                      className="p-1 rounded-md text-slate-500 hover:text-blue-700 hover:bg-slate-100"
                    >
                      <Edit2 className="w-3.5 h-3.5" />
                    </button>
                    <button
                      onClick={() => setStudentToDelete(student)}
                      title="মুছুন"
                      className="p-1 rounded-md text-slate-500 hover:text-rose-700 hover:bg-slate-100"
                    >
                      <Trash2 className="w-3.5 h-3.5" />
                    </button>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Form Modal (Add / Edit) */}
      <StudentFormModal
        isOpen={isFormOpen}
        onClose={() => {
          setIsFormOpen(false);
          setEditingStudent(null);
        }}
        studentToEdit={editingStudent}
      />

      {/* Detail Modal */}
      <StudentDetailModal
        isOpen={!!viewingStudent}
        student={viewingStudent}
        onClose={() => setViewingStudent(null)}
        onEdit={s => {
          setViewingStudent(null);
          setEditingStudent(s);
          setIsFormOpen(true);
        }}
        onOpenIdCard={s => {
          setViewingStudent(null);
          setIdCardStudent(s);
        }}
      />

      {/* ID Card Modal */}
      <StudentIdCardModal
        isOpen={!!idCardStudent}
        student={idCardStudent}
        onClose={() => setIdCardStudent(null)}
      />

      {/* Bulk Actions Modal */}
      <StudentBulkActionsModal
        isOpen={showBulkModal}
        selectedStudentIds={selectedStudentIds}
        onClose={() => setShowBulkModal(false)}
        onComplete={() => {
          setSelectedStudentIds([]);
          setIsSelectionMode(false);
        }}
      />

      {/* Import / Export Modal */}
      <StudentImportExportModal
        isOpen={showImportExport}
        onClose={() => setShowImportExport(false)}
      />

      {/* Single Delete Confirmation Dialog */}
      {studentToDelete && (
        <div className="fixed inset-0 z-50 overflow-y-auto bg-slate-900/60 backdrop-blur-xs flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl shadow-2xl max-w-sm w-full p-6 text-center space-y-4 animate-in fade-in zoom-in-95">
            <div className="w-12 h-12 rounded-full bg-rose-100 text-rose-600 flex items-center justify-center mx-auto">
              <AlertCircle className="w-6 h-6" />
            </div>
            <h3 className="font-bold text-slate-900 text-base">শিক্ষার্থী মুছতে চান?</h3>
            <p className="text-xs text-slate-500">
              আপনি কি নিশ্চিত যে <strong>{studentToDelete.name}</strong> ({studentToDelete.studentClass}, রোল {toBanglaDigits(studentToDelete.rollNumber)})-এর সকল তথ্য স্থায়ীভাবে মুছে ফেলতে চান?
            </p>
            <div className="flex gap-2 justify-center pt-2">
              <button
                onClick={() => setStudentToDelete(null)}
                className="px-4 py-2 text-xs font-semibold rounded-lg border border-slate-300 text-slate-700 hover:bg-slate-50"
              >
                বাতিল
              </button>
              <button
                onClick={() => {
                  deleteStudent(studentToDelete.id);
                  setStudentToDelete(null);
                }}
                className="px-4 py-2 text-xs font-bold rounded-lg bg-rose-600 hover:bg-rose-700 text-white shadow-md"
              >
                হ্যাঁ, মুছে ফেলুন
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
