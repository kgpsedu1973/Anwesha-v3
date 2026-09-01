import React, { useState, useEffect } from 'react';
import {
  X,
  User,
  Calendar,
  Phone,
  MapPin,
  Heart,
  Upload,
  Camera,
  Check,
  Sparkles,
  Info
} from 'lucide-react';
import { Student } from '../../types';
import { useApp } from '../../context/AppContext';
import { calculateAge, toBanglaDigits, getTodayDateStr } from '../../utils/banglaUtils';
import { FormulaEvaluator } from '../../utils/formulaEvaluator';

interface StudentFormModalProps {
  isOpen: boolean;
  onClose: () => void;
  studentToEdit?: Student | null;
}

export const StudentFormModal: React.FC<StudentFormModalProps> = ({
  isOpen,
  onClose,
  studentToEdit
}) => {
  const {
    addStudent,
    updateStudent,
    customFields,
    formulaRules,
    schoolInfo,
    genderTerminology
  } = useApp();

  const [formData, setFormData] = useState({
    name: '',
    studentClass: '১ম শ্রেণি',
    section: 'ক',
    rollNumber: 1,
    parentContact: '',
    fatherName: '',
    motherName: '',
    birthDate: '2019-01-01',
    mobile: '',
    village: 'পশ্চিম রামপুর',
    academicYear: '২০২৬',
    address: '',
    birthRegNumber: '',
    gender: 'ছাত্র',
    isSpecialNeeds: false,
    status: 'Current' as Student['status'],
    photoUri: null as string | null,
    admissionDate: getTodayDateStr(),
    customValues: {} as Record<string, string>
  });

  const [liveAge, setLiveAge] = useState('');

  useEffect(() => {
    if (studentToEdit) {
      setFormData({
        name: studentToEdit.name || '',
        studentClass: studentToEdit.studentClass || '১ম শ্রেণি',
        section: studentToEdit.section || 'ক',
        rollNumber: studentToEdit.rollNumber || 1,
        parentContact: studentToEdit.parentContact || '',
        fatherName: studentToEdit.fatherName || '',
        motherName: studentToEdit.motherName || '',
        birthDate: studentToEdit.birthDate || '2019-01-01',
        mobile: studentToEdit.mobile || '',
        village: studentToEdit.village || 'পশ্চিম রামপুর',
        academicYear: studentToEdit.academicYear || '২০২৬',
        address: studentToEdit.address || '',
        birthRegNumber: studentToEdit.birthRegNumber || '',
        gender: studentToEdit.gender || 'ছাত্র',
        isSpecialNeeds: studentToEdit.isSpecialNeeds || false,
        status: studentToEdit.status || 'Current',
        photoUri: studentToEdit.photoUri || null,
        admissionDate: studentToEdit.admissionDate || getTodayDateStr(),
        customValues: studentToEdit.customValues || {}
      });
    } else {
      setFormData({
        name: '',
        studentClass: '১ম শ্রেণি',
        section: 'ক',
        rollNumber: 1,
        parentContact: '',
        fatherName: '',
        motherName: '',
        birthDate: '2019-01-01',
        mobile: '',
        village: 'পশ্চিম রামপুর',
        academicYear: '২০২৬',
        address: '',
        birthRegNumber: '',
        gender: 'ছাত্র',
        isSpecialNeeds: false,
        status: 'Current',
        photoUri: null,
        admissionDate: getTodayDateStr(),
        customValues: {}
      });
    }
  }, [studentToEdit, isOpen]);

  // Update live age whenever birthDate changes
  useEffect(() => {
    if (formData.birthDate) {
      const res = calculateAge(formData.birthDate);
      setLiveAge(res.formattedText);
    } else {
      setLiveAge('');
    }
  }, [formData.birthDate]);

  if (!isOpen) return null;

  const standardClasses = [
    'প্রাক-প্রাথমিক ৪+',
    'প্রাক-প্রাথমিক ৫+',
    '১ম শ্রেণি',
    '২য় শ্রেণি',
    '৩য় শ্রেণি',
    '৪র্থ শ্রেণি',
    '৫ম শ্রেণি'
  ];

  const handlePhotoUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      const reader = new FileReader();
      reader.onloadend = () => {
        setFormData(prev => ({ ...prev, photoUri: reader.result as string }));
      };
      reader.readAsDataURL(file);
    }
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!formData.name.trim()) {
      alert('শিক্ষার্থীর নাম প্রদান করুন');
      return;
    }

    if (studentToEdit) {
      updateStudent(studentToEdit.id, {
        ...formData,
        lastModifiedDate: getTodayDateStr()
      });
    } else {
      addStudent({
        ...formData,
        lastModifiedDate: getTodayDateStr()
      });
    }

    onClose();
  };

  // Preview category calculation
  const previewStudent: Student = {
    id: studentToEdit?.id || 'temp',
    ...formData,
    createdAt: 0,
    updatedAt: 0,
    lastModifiedDate: ''
  };
  const categoryPreview = FormulaEvaluator.getStudentCategory(previewStudent, schoolInfo.internalVillages);

  return (
    <div className="fixed inset-0 z-50 overflow-y-auto bg-slate-900/60 backdrop-blur-xs flex items-center justify-center p-4">
      <div className="bg-white rounded-2xl shadow-2xl max-w-3xl w-full overflow-hidden border border-slate-200 animate-in fade-in zoom-in-95 duration-200">
        {/* Header */}
        <div className="bg-gradient-to-r from-emerald-700 to-teal-700 text-white px-6 py-4 flex items-center justify-between">
          <div className="flex items-center gap-2.5">
            <div className="w-8 h-8 rounded-lg bg-white/15 flex items-center justify-center">
              <User className="w-5 h-5" />
            </div>
            <div>
              <h2 className="font-bold text-base font-serif-bn">
                {studentToEdit ? 'শিক্ষার্থীর তথ্য সম্পাদন' : 'নতুন শিক্ষার্থী ভর্তি ফরম'}
              </h2>
              <p className="text-xs text-emerald-100/90">
                {schoolInfo.schoolName}
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="text-white/80 hover:text-white p-1 rounded-lg hover:bg-white/10 transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Form Body */}
        <form onSubmit={handleSubmit} className="p-6 space-y-6 max-h-[80vh] overflow-y-auto">
          {/* Top Section: Photo & Basic Info */}
          <div className="flex flex-col sm:flex-row gap-6 items-start">
            {/* Photo Avatar Picker */}
            <div className="flex flex-col items-center gap-2 self-center sm:self-start">
              <div className="relative w-28 h-28 rounded-2xl border-2 border-dashed border-slate-300 bg-slate-50 flex items-center justify-center overflow-hidden group">
                {formData.photoUri ? (
                  <img
                    src={formData.photoUri}
                    alt="Student"
                    className="w-full h-full object-cover"
                  />
                ) : (
                  <div className="text-center p-2">
                    <Camera className="w-6 h-6 text-slate-400 mx-auto mb-1" />
                    <span className="text-[10px] text-slate-500 font-medium">ছবি নির্বাচন</span>
                  </div>
                )}
                <label className="absolute inset-0 bg-slate-900/40 text-white opacity-0 group-hover:opacity-100 flex flex-col items-center justify-center cursor-pointer transition-opacity text-xs font-semibold">
                  <Upload className="w-4 h-4 mb-1" />
                  <span>ছবি পরিবর্তন</span>
                  <input
                    type="file"
                    accept="image/*"
                    onChange={handlePhotoUpload}
                    className="hidden"
                  />
                </label>
              </div>
              {formData.photoUri && (
                <button
                  type="button"
                  onClick={() => setFormData(prev => ({ ...prev, photoUri: null }))}
                  className="text-[11px] text-rose-600 hover:underline"
                >
                  ছবি মুছুন
                </button>
              )}
            </div>

            {/* Core Fields */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 flex-1 w-full">
              <div className="sm:col-span-2">
                <label className="block text-xs font-semibold text-slate-700 mb-1">
                  শিক্ষার্থীর পূর্ণ নাম <span className="text-rose-500">*</span>
                </label>
                <input
                  type="text"
                  required
                  value={formData.name}
                  onChange={e => setFormData({ ...formData, name: e.target.value })}
                  placeholder="যেমন: তানভীর আহমেদ"
                  className="w-full px-3 py-2 text-sm rounded-lg border border-slate-300 focus:border-emerald-500 focus:ring-2 focus:ring-emerald-500/20 outline-hidden font-medium"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">
                  শ্রেণি <span className="text-rose-500">*</span>
                </label>
                <select
                  value={formData.studentClass}
                  onChange={e => setFormData({ ...formData, studentClass: e.target.value })}
                  className="w-full px-3 py-2 text-sm rounded-lg border border-slate-300 focus:border-emerald-500 focus:ring-2 focus:ring-emerald-500/20 outline-hidden"
                >
                  {standardClasses.map(cls => (
                    <option key={cls} value={cls}>
                      {cls}
                    </option>
                  ))}
                </select>
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">
                  রোল নম্বর <span className="text-rose-500">*</span>
                </label>
                <input
                  type="number"
                  required
                  min={1}
                  value={formData.rollNumber}
                  onChange={e => setFormData({ ...formData, rollNumber: parseInt(e.target.value) || 1 })}
                  className="w-full px-3 py-2 text-sm rounded-lg border border-slate-300 focus:border-emerald-500 focus:ring-2 focus:ring-emerald-500/20 outline-hidden"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">শাখা</label>
                <input
                  type="text"
                  value={formData.section}
                  onChange={e => setFormData({ ...formData, section: e.target.value })}
                  placeholder="ক, খ, বা প্রযোজ্য নয়"
                  className="w-full px-3 py-2 text-sm rounded-lg border border-slate-300 focus:border-emerald-500 focus:ring-2 focus:ring-emerald-500/20 outline-hidden"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">
                  লিঙ্গ
                </label>
                <select
                  value={formData.gender}
                  onChange={e => setFormData({ ...formData, gender: e.target.value })}
                  className="w-full px-3 py-2 text-sm rounded-lg border border-slate-300 focus:border-emerald-500 focus:ring-2 focus:ring-emerald-500/20 outline-hidden"
                >
                  <option value="ছাত্র">{genderTerminology.boyLabel} (ছাত্র/বালক)</option>
                  <option value="ছাত্রী">{genderTerminology.girlLabel} (ছাত্রী/বালিকা)</option>
                </select>
              </div>
            </div>
          </div>

          {/* Age & Date of Birth Section */}
          <div className="p-4 rounded-xl bg-slate-50 border border-slate-200/80 space-y-3">
            <div className="flex items-center justify-between">
              <span className="text-xs font-bold text-slate-700 flex items-center gap-1.5">
                <Calendar className="w-4 h-4 text-emerald-600" />
                <span>জন্মতারিখ ও লাইভ বয়স</span>
              </span>
              {liveAge && (
                <span className="text-xs font-semibold text-emerald-700 bg-emerald-100/70 px-2.5 py-0.5 rounded-full">
                  বর্তমান বয়স: {liveAge}
                </span>
              )}
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div>
                <label className="block text-xs font-semibold text-slate-600 mb-1">
                  জন্ম তারিখ (YYYY-MM-DD)
                </label>
                <input
                  type="date"
                  value={formData.birthDate}
                  onChange={e => setFormData({ ...formData, birthDate: e.target.value })}
                  className="w-full px-3 py-2 text-sm rounded-lg border border-slate-300 focus:border-emerald-500 focus:ring-2 focus:ring-emerald-500/20 outline-hidden"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-600 mb-1">
                  ১৭ সংখ্যার জন্ম নিবন্ধন নম্বর
                </label>
                <input
                  type="text"
                  value={formData.birthRegNumber}
                  onChange={e => setFormData({ ...formData, birthRegNumber: e.target.value })}
                  placeholder="যেমন: 20191912834000101"
                  className="w-full px-3 py-2 text-sm rounded-lg border border-slate-300 focus:border-emerald-500 focus:ring-2 focus:ring-emerald-500/20 outline-hidden font-mono text-xs"
                />
              </div>
            </div>
          </div>

          {/* Parents & Contact Information */}
          <div className="space-y-4">
            <h3 className="text-xs font-bold text-slate-800 uppercase tracking-wider border-b border-slate-100 pb-2">
              পারিবারিক ও যোগাযোগের তথ্য
            </h3>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">পিতার নাম</label>
                <input
                  type="text"
                  value={formData.fatherName}
                  onChange={e => setFormData({ ...formData, fatherName: e.target.value })}
                  placeholder="পিতার নাম"
                  className="w-full px-3 py-2 text-sm rounded-lg border border-slate-300 focus:border-emerald-500 focus:ring-2 focus:ring-emerald-500/20 outline-hidden"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">মাতার নাম</label>
                <input
                  type="text"
                  value={formData.motherName}
                  onChange={e => setFormData({ ...formData, motherName: e.target.value })}
                  placeholder="মাতার নাম"
                  className="w-full px-3 py-2 text-sm rounded-lg border border-slate-300 focus:border-emerald-500 focus:ring-2 focus:ring-emerald-500/20 outline-hidden"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">
                  অভিভাবকের মোবাইল নম্বর
                </label>
                <input
                  type="tel"
                  value={formData.mobile}
                  onChange={e => setFormData({ ...formData, mobile: e.target.value, parentContact: e.target.value })}
                  placeholder="01XXXXXXXXX"
                  className="w-full px-3 py-2 text-sm rounded-lg border border-slate-300 focus:border-emerald-500 focus:ring-2 focus:ring-emerald-500/20 outline-hidden"
                />
              </div>

              <div>
                <div className="flex items-center justify-between mb-1">
                  <label className="block text-xs font-semibold text-slate-700">
                    গ্রাম / এলাকা
                  </label>
                  <span className="text-[11px] text-emerald-700 font-medium flex items-center gap-1">
                    <Sparkles className="w-3 h-3" />
                    সূত্র: <strong className="text-slate-800">{categoryPreview}</strong>
                  </span>
                </div>
                <input
                  type="text"
                  value={formData.village}
                  onChange={e => setFormData({ ...formData, village: e.target.value })}
                  placeholder="যেমন: পশ্চিম রামপুর"
                  className="w-full px-3 py-2 text-sm rounded-lg border border-slate-300 focus:border-emerald-500 focus:ring-2 focus:ring-emerald-500/20 outline-hidden"
                />
              </div>
            </div>
          </div>

          {/* Dynamic Custom Fields Section */}
          {customFields.length > 0 && (
            <div className="space-y-4 p-4 rounded-xl bg-slate-50 border border-slate-200/80">
              <h3 className="text-xs font-bold text-slate-800 uppercase tracking-wider flex items-center gap-1.5">
                <Sparkles className="w-4 h-4 text-indigo-600" />
                <span>কাস্টম ফিল্ড ও ডায়নামিক তথ্য</span>
              </h3>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                {customFields.map(field => {
                  if (field.isCalculated) {
                    const val = FormulaEvaluator.getFieldValue(
                      previewStudent,
                      field.id,
                      customFields,
                      formulaRules
                    );
                    return (
                      <div key={field.id} className="p-2.5 rounded-lg bg-white border border-slate-200">
                        <label className="block text-xs font-semibold text-slate-500 mb-0.5">
                          {field.name} (স্বয়ংক্রিয় গণনাকৃত)
                        </label>
                        <span className="font-bold text-sm text-indigo-700">{val || '—'}</span>
                      </div>
                    );
                  }

                  if (field.fieldType === 'Dropdown') {
                    return (
                      <div key={field.id}>
                        <label className="block text-xs font-semibold text-slate-700 mb-1">
                          {field.name}
                        </label>
                        <select
                          value={formData.customValues[field.id] || ''}
                          onChange={e =>
                            setFormData({
                              ...formData,
                              customValues: { ...formData.customValues, [field.id]: e.target.value }
                            })
                          }
                          className="w-full px-3 py-2 text-sm rounded-lg border border-slate-300 focus:border-emerald-500 focus:ring-2 focus:ring-emerald-500/20 outline-hidden"
                        >
                          <option value="">নির্বাচন করুন</option>
                          {field.options.map(opt => (
                            <option key={opt} value={opt}>
                              {opt}
                            </option>
                          ))}
                        </select>
                      </div>
                    );
                  }

                  return (
                    <div key={field.id}>
                      <label className="block text-xs font-semibold text-slate-700 mb-1">
                        {field.name}
                      </label>
                      <input
                        type={field.fieldType === 'Number' ? 'number' : 'text'}
                        value={formData.customValues[field.id] || ''}
                        onChange={e =>
                          setFormData({
                            ...formData,
                            customValues: { ...formData.customValues, [field.id]: e.target.value }
                          })
                        }
                        placeholder={field.name}
                        className="w-full px-3 py-2 text-sm rounded-lg border border-slate-300 focus:border-emerald-500 focus:ring-2 focus:ring-emerald-500/20 outline-hidden"
                      />
                    </div>
                  );
                })}
              </div>
            </div>
          )}

          {/* Status and Special Needs Toggles */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 pt-2 border-t border-slate-100">
            <div className="flex items-center gap-3 p-3 rounded-xl border border-slate-200">
              <input
                type="checkbox"
                id="isSpecialNeeds"
                checked={formData.isSpecialNeeds}
                onChange={e => setFormData({ ...formData, isSpecialNeeds: e.target.checked })}
                className="w-4 h-4 text-emerald-600 rounded-sm focus:ring-emerald-500 border-slate-300"
              />
              <label htmlFor="isSpecialNeeds" className="text-xs font-medium text-slate-700 cursor-pointer">
                বিশেষ চাহিদা সম্পন্ন শিক্ষার্থী
              </label>
            </div>

            <div>
              <label className="block text-xs font-semibold text-slate-700 mb-1">
                শিক্ষার্থীর অবস্থা (Status)
              </label>
              <select
                value={formData.status}
                onChange={e => setFormData({ ...formData, status: e.target.value as Student['status'] })}
                className="w-full px-3 py-2 text-sm rounded-lg border border-slate-300 focus:border-emerald-500 focus:ring-2 focus:ring-emerald-500/20 outline-hidden"
              >
                <option value="Current">নিয়মিত (Current)</option>
                <option value="Former">প্রাক্তন (Former)</option>
                <option value="Transferred">ছাড়পত্রপ্রাপ্ত (Transferred)</option>
                <option value="Inactive">নিষ্ক্রিয় (Inactive)</option>
              </select>
            </div>
          </div>

          {/* Action Buttons */}
          <div className="flex items-center justify-end gap-3 pt-4 border-t border-slate-200">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2 rounded-lg border border-slate-300 hover:bg-slate-50 text-slate-700 text-sm font-medium transition-colors cursor-pointer"
            >
              বাতিল
            </button>
            <button
              type="submit"
              className="inline-flex items-center gap-2 px-5 py-2 rounded-lg bg-emerald-600 hover:bg-emerald-700 text-white text-sm font-semibold shadow-md transition-colors cursor-pointer"
            >
              <Check className="w-4 h-4" />
              <span>{studentToEdit ? 'সংরক্ষণ করুন' : 'ভর্তি সম্পন্ন করুন'}</span>
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
