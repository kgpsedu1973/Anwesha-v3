import React, { useState } from 'react';
import { X, Users, Check, Trash2, ArrowRight } from 'lucide-react';
import { useApp } from '../../context/AppContext';
import { Student } from '../../types';
import { toBanglaDigits } from '../../utils/banglaUtils';

interface StudentBulkActionsModalProps {
  isOpen: boolean;
  onClose: () => void;
  selectedStudentIds: string[];
  onComplete: () => void;
}

export const StudentBulkActionsModal: React.FC<StudentBulkActionsModalProps> = ({
  isOpen,
  onClose,
  selectedStudentIds,
  onComplete
}) => {
  const {
    bulkDeleteStudents,
    bulkUpdateClass,
    bulkUpdateSection,
    bulkUpdateStatus
  } = useApp();

  const [actionType, setActionType] = useState<'class' | 'section' | 'status' | 'delete'>('class');
  const [targetClass, setTargetClass] = useState('২য় শ্রেণি');
  const [targetSection, setTargetSection] = useState('ক');
  const [targetStatus, setTargetStatus] = useState<Student['status']>('Current');

  if (!isOpen || selectedStudentIds.length === 0) return null;

  const standardClasses = [
    'প্রাক-প্রাথমিক ৪+',
    'প্রাক-প্রাথমিক ৫+',
    '১ম শ্রেণি',
    '২য় শ্রেণি',
    '৩য় শ্রেণি',
    '৪র্থ শ্রেণি',
    '৫ম শ্রেণি'
  ];

  const handleApply = () => {
    if (actionType === 'class') {
      bulkUpdateClass(selectedStudentIds, targetClass);
    } else if (actionType === 'section') {
      bulkUpdateSection(selectedStudentIds, targetSection);
    } else if (actionType === 'status') {
      bulkUpdateStatus(selectedStudentIds, targetStatus);
    } else if (actionType === 'delete') {
      if (confirm(`আপনি কি নিশ্চিত যে নির্বাচিত ${selectedStudentIds.length} জন শিক্ষার্থীকে মুছে ফেলতে চান?`)) {
        bulkDeleteStudents(selectedStudentIds);
      } else {
        return;
      }
    }
    onComplete();
    onClose();
  };

  return (
    <div className="fixed inset-0 z-50 overflow-y-auto bg-slate-900/60 backdrop-blur-xs flex items-center justify-center p-4">
      <div className="bg-white rounded-2xl shadow-2xl max-w-md w-full overflow-hidden border border-slate-200 animate-in fade-in zoom-in-95 duration-200">
        <div className="bg-slate-900 text-white px-5 py-4 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Users className="w-5 h-5 text-emerald-400" />
            <h3 className="font-bold text-sm">
              একত্রে পরিবর্তন ({toBanglaDigits(selectedStudentIds.length)} জন শিক্ষার্থী)
            </h3>
          </div>
          <button onClick={onClose} className="text-slate-400 hover:text-white">
            <X className="w-4 h-4" />
          </button>
        </div>

        <div className="p-6 space-y-4">
          <div className="space-y-2">
            <label className="block text-xs font-semibold text-slate-700">অ্যাকশন নির্বাচন করুন</label>
            <div className="grid grid-cols-2 gap-2">
              <button
                type="button"
                onClick={() => setActionType('class')}
                className={`p-2.5 rounded-xl border text-xs font-semibold transition-all text-left ${
                  actionType === 'class'
                    ? 'border-emerald-600 bg-emerald-50 text-emerald-800'
                    : 'border-slate-200 hover:bg-slate-50 text-slate-700'
                }`}
              >
                শ্রেণি পরিবর্তন
              </button>
              <button
                type="button"
                onClick={() => setActionType('section')}
                className={`p-2.5 rounded-xl border text-xs font-semibold transition-all text-left ${
                  actionType === 'section'
                    ? 'border-emerald-600 bg-emerald-50 text-emerald-800'
                    : 'border-slate-200 hover:bg-slate-50 text-slate-700'
                }`}
              >
                শাখা পরিবর্তন
              </button>
              <button
                type="button"
                onClick={() => setActionType('status')}
                className={`p-2.5 rounded-xl border text-xs font-semibold transition-all text-left ${
                  actionType === 'status'
                    ? 'border-emerald-600 bg-emerald-50 text-emerald-800'
                    : 'border-slate-200 hover:bg-slate-50 text-slate-700'
                }`}
              >
                স্ট্যাটাস পরিবর্তন
              </button>
              <button
                type="button"
                onClick={() => setActionType('delete')}
                className={`p-2.5 rounded-xl border text-xs font-semibold transition-all text-left ${
                  actionType === 'delete'
                    ? 'border-rose-600 bg-rose-50 text-rose-800'
                    : 'border-slate-200 hover:bg-slate-50 text-slate-700'
                }`}
              >
                একত্রে মুছুন
              </button>
            </div>
          </div>

          {/* Action Specific Fields */}
          {actionType === 'class' && (
            <div className="space-y-1">
              <label className="block text-xs font-semibold text-slate-700">নতুন শ্রেণি</label>
              <select
                value={targetClass}
                onChange={e => setTargetClass(e.target.value)}
                className="w-full px-3 py-2 text-sm rounded-lg border border-slate-300 focus:border-emerald-500 outline-hidden"
              >
                {standardClasses.map(cls => (
                  <option key={cls} value={cls}>
                    {cls}
                  </option>
                ))}
              </select>
            </div>
          )}

          {actionType === 'section' && (
            <div className="space-y-1">
              <label className="block text-xs font-semibold text-slate-700">নতুন শাখা</label>
              <input
                type="text"
                value={targetSection}
                onChange={e => setTargetSection(e.target.value)}
                placeholder="ক, খ, বা প্রযোজ্য নয়"
                className="w-full px-3 py-2 text-sm rounded-lg border border-slate-300 focus:border-emerald-500 outline-hidden"
              />
            </div>
          )}

          {actionType === 'status' && (
            <div className="space-y-1">
              <label className="block text-xs font-semibold text-slate-700">নতুন স্ট্যাটাস</label>
              <select
                value={targetStatus}
                onChange={e => setTargetStatus(e.target.value as any)}
                className="w-full px-3 py-2 text-sm rounded-lg border border-slate-300 focus:border-emerald-500 outline-hidden"
              >
                <option value="Current">নিয়মিত (Current)</option>
                <option value="Former">প্রাক্তন (Former)</option>
                <option value="Transferred">ছাড়পত্রপ্রাপ্ত (Transferred)</option>
                <option value="Inactive">নিষ্ক্রিয় (Inactive)</option>
              </select>
            </div>
          )}

          {actionType === 'delete' && (
            <div className="p-3 rounded-xl bg-rose-50 border border-rose-200 text-rose-800 text-xs leading-relaxed">
              সতর্কতা: নির্বাচিত {toBanglaDigits(selectedStudentIds.length)} জন শিক্ষার্থীর সকল রেকর্ড স্থায়ীভাবে মুছে যাবে।
            </div>
          )}

          <div className="flex items-center justify-end gap-3 pt-3 border-t border-slate-200">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2 rounded-lg border border-slate-300 text-slate-700 text-xs font-medium cursor-pointer"
            >
              বাতিল
            </button>
            <button
              type="button"
              onClick={handleApply}
              className={`px-4 py-2 rounded-lg text-white text-xs font-bold transition-colors cursor-pointer ${
                actionType === 'delete' ? 'bg-rose-600 hover:bg-rose-700' : 'bg-emerald-600 hover:bg-emerald-700'
              }`}
            >
              প্রয়োগ করুন
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};
