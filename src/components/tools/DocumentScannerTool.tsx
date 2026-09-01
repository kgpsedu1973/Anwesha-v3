import React, { useState } from 'react';
import {
  FileText,
  ArrowLeft,
  Upload,
  Camera,
  Trash2,
  Eye,
  Download,
  Filter,
  Plus,
  Sparkles,
  CheckCircle2,
  Search
} from 'lucide-react';
import { useApp } from '../../context/AppContext';
import { toBanglaDigits, formatBanglaDate } from '../../utils/banglaUtils';
import { StudentDocument } from '../../types';

export const DocumentScannerTool: React.FC<{ onBack: () => void }> = ({ onBack }) => {
  const {
    students,
    studentDocuments,
    addStudentDocument,
    deleteStudentDocument,
    showToast
  } = useApp();

  const [selectedStudentId, setSelectedStudentId] = useState<string>('ALL');
  const [selectedDocType, setSelectedDocType] = useState<string>('ALL');
  const [searchQuery, setSearchQuery] = useState('');

  // New Doc Form
  const [isAdding, setIsAdding] = useState(false);
  const [newStudentId, setNewStudentId] = useState(students[0]?.id || '');
  const [newTitle, setNewTitle] = useState('');
  const [newType, setNewType] = useState<StudentDocument['docType']>('BirthCert');
  const [newImage, setNewImage] = useState<string | null>(null);

  // Lightbox view
  const [previewDoc, setPreviewDoc] = useState<StudentDocument | null>(null);

  const filteredDocs = studentDocuments.filter(doc => {
    if (selectedStudentId !== 'ALL' && doc.studentId !== selectedStudentId) return false;
    if (selectedDocType !== 'ALL' && doc.docType !== selectedDocType) return false;
    if (searchQuery) {
      const q = searchQuery.toLowerCase();
      const student = students.find(s => s.id === doc.studentId);
      const sName = student?.name.toLowerCase() || '';
      return doc.title.toLowerCase().includes(q) || sName.includes(q);
    }
    return true;
  });

  const handleFileUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      const reader = new FileReader();
      reader.onloadend = () => setNewImage(reader.result as string);
      reader.readAsDataURL(file);
    }
  };

  const handleSaveDoc = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newTitle.trim() || !newImage) {
      showToast('শিরোনাম এবং ডকুমেন্টের ছবি আবশ্যক', 'error');
      return;
    }

    addStudentDocument({
      studentId: newStudentId,
      title: newTitle,
      docType: newType,
      imageUri: newImage
    });

    setIsAdding(false);
    setNewTitle('');
    setNewImage(null);
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
                ডকুমেন্ট স্ক্যানার ও আর্কাইভ
              </h1>
              <span className="text-xs px-2.5 py-0.5 rounded-full bg-teal-100 text-teal-800 font-bold">
                ডিজিটাল ফাইল
              </span>
            </div>
            <p className="text-xs text-slate-500 mt-0.5">
              শিক্ষার্থীদের জন্মনিবন্ধন, NID, প্রশংসাপত্র ও ছবি ডিজিটাল আর্কাইভে সংরক্ষণ
            </p>
          </div>
        </div>

        <button
          onClick={() => setIsAdding(true)}
          className="inline-flex items-center gap-1.5 px-4 py-2 rounded-lg bg-teal-600 hover:bg-teal-700 text-white text-xs font-bold shadow-md shadow-teal-600/20 cursor-pointer"
        >
          <Plus className="w-4 h-4" />
          <span>নতুন ডকুমেন্ট স্ক্যান / আপলোড</span>
        </button>
      </div>

      {/* Filter and Search Bar */}
      <div className="bg-white p-4 rounded-2xl border border-slate-200/80 shadow-xs flex flex-wrap items-center gap-3">
        <div className="relative flex-1 min-w-[200px]">
          <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
          <input
            type="text"
            placeholder="ডকুমেন্টের নাম বা শিক্ষার্থী..."
            value={searchQuery}
            onChange={e => setSearchQuery(e.target.value)}
            className="w-full pl-9 pr-3 py-1.5 text-xs rounded-xl bg-slate-50 border border-slate-200"
          />
        </div>

        <select
          value={selectedStudentId}
          onChange={e => setSelectedStudentId(e.target.value)}
          className="px-3 py-1.5 text-xs rounded-xl bg-slate-50 border border-slate-200"
        >
          <option value="ALL">সকল শিক্ষার্থী</option>
          {students.map(s => (
            <option key={s.id} value={s.id}>
              {s.name} ({s.studentClass})
            </option>
          ))}
        </select>

        <select
          value={selectedDocType}
          onChange={e => setSelectedDocType(e.target.value)}
          className="px-3 py-1.5 text-xs rounded-xl bg-slate-50 border border-slate-200"
        >
          <option value="ALL">সকল ধরনের ডকুমেন্ট</option>
          <option value="BirthCert">জন্ম নিবন্ধন সনদ</option>
          <option value="NID">অভিভাবকের NID</option>
          <option value="Photo">ছবি</option>
          <option value="ReportCard">নম্বরপত্র</option>
          <option value="Certificate">সনদপত্র</option>
          <option value="Other">অন্যান্য</option>
        </select>
      </div>

      {/* Documents Grid */}
      {filteredDocs.length === 0 ? (
        <div className="bg-white p-12 rounded-2xl border border-slate-200 text-center space-y-2">
          <FileText className="w-12 h-12 text-slate-300 mx-auto" />
          <h3 className="font-bold text-sm text-slate-700">কোন ডকুমেন্ট পাওয়া যায়নি</h3>
          <p className="text-xs text-slate-400">নতুন ডকুমেন্ট আপলোড করতে উপরের বাটনে ক্লিক করুন</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-4">
          {filteredDocs.map(doc => {
            const student = students.find(s => s.id === doc.studentId);
            return (
              <div
                key={doc.id}
                className="bg-white rounded-2xl border border-slate-200/80 p-3 shadow-xs space-y-2.5 group hover:border-teal-400 transition-all"
              >
                <div
                  onClick={() => setPreviewDoc(doc)}
                  className="h-44 rounded-xl bg-slate-100 border border-slate-200 overflow-hidden cursor-pointer flex items-center justify-center relative"
                >
                  <img src={doc.imageUri} alt={doc.title} className="w-full h-full object-cover group-hover:scale-105 transition-transform" />
                  <div className="absolute inset-0 bg-slate-950/30 opacity-0 group-hover:opacity-100 flex items-center justify-center text-white transition-opacity">
                    <Eye className="w-6 h-6" />
                  </div>
                </div>

                <div className="space-y-0.5">
                  <h4 className="font-bold text-xs text-slate-800 truncate" title={doc.title}>
                    {doc.title}
                  </h4>
                  <p className="text-[11px] text-slate-500 truncate">
                    শিক্ষার্থী: <strong className="text-slate-700">{student?.name || 'অজ্ঞাত'}</strong>
                  </p>
                  <div className="flex items-center justify-between text-[10px] text-slate-400 pt-1 border-t border-slate-100">
                    <span>{formatBanglaDate(doc.uploadedAt)}</span>
                    <button
                      onClick={() => deleteStudentDocument(doc.id)}
                      className="text-rose-500 hover:text-rose-700 p-1"
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

      {/* Upload Modal */}
      {isAdding && (
        <div className="fixed inset-0 z-50 overflow-y-auto bg-slate-900/60 backdrop-blur-xs flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl shadow-2xl max-w-md w-full p-6 space-y-4 animate-in fade-in zoom-in-95">
            <h3 className="font-bold text-slate-900 text-sm font-serif-bn border-b border-slate-100 pb-2">
              নতুন ডকুমেন্ট আপলোড / স্ক্যান
            </h3>

            <form onSubmit={handleSaveDoc} className="space-y-4 text-xs">
              <div>
                <label className="block font-semibold text-slate-700 mb-1">শিক্ষার্থী</label>
                <select
                  value={newStudentId}
                  onChange={e => setNewStudentId(e.target.value)}
                  className="w-full px-3 py-2 rounded-lg border border-slate-300 bg-slate-50 font-medium"
                >
                  {students.map(s => (
                    <option key={s.id} value={s.id}>
                      {s.name} ({s.studentClass}, রোল {toBanglaDigits(s.rollNumber)})
                    </option>
                  ))}
                </select>
              </div>

              <div>
                <label className="block font-semibold text-slate-700 mb-1">ডকুমেন্টের শিরোনাম</label>
                <input
                  type="text"
                  required
                  placeholder="যেমন: জন্ম নিবন্ধন সনদ কপি"
                  value={newTitle}
                  onChange={e => setNewTitle(e.target.value)}
                  className="w-full px-3 py-2 rounded-lg border border-slate-300 bg-slate-50"
                />
              </div>

              <div>
                <label className="block font-semibold text-slate-700 mb-1">ডকুমেন্টের ধরন</label>
                <select
                  value={newType}
                  onChange={e => setNewType(e.target.value as any)}
                  className="w-full px-3 py-2 rounded-lg border border-slate-300 bg-slate-50"
                >
                  <option value="BirthCert">জন্ম নিবন্ধন সনদ</option>
                  <option value="NID">অভিভাবকের NID</option>
                  <option value="Photo">ছবি</option>
                  <option value="ReportCard">নম্বরপত্র</option>
                  <option value="Certificate">সনদপত্র</option>
                  <option value="Other">অন্যান্য</option>
                </select>
              </div>

              <div>
                <label className="block font-semibold text-slate-700 mb-1">ডকুমেন্ট ফাইল নির্বাচন</label>
                <input
                  type="file"
                  accept="image/*"
                  required
                  onChange={handleFileUpload}
                  className="block w-full text-slate-500 file:mr-3 file:py-1.5 file:px-3 file:rounded-lg file:border-0 file:text-xs file:font-semibold file:bg-teal-50 file:text-teal-700 hover:file:bg-teal-100"
                />
              </div>

              <div className="flex justify-end gap-2 pt-3 border-t border-slate-100">
                <button
                  type="button"
                  onClick={() => setIsAdding(false)}
                  className="px-4 py-2 rounded-lg border border-slate-300 text-slate-700 font-medium"
                >
                  বাতিল
                </button>
                <button
                  type="submit"
                  className="px-4 py-2 rounded-lg bg-teal-600 hover:bg-teal-700 text-white font-bold"
                >
                  সংরক্ষণ করুন
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Lightbox / Preview Modal */}
      {previewDoc && (
        <div
          onClick={() => setPreviewDoc(null)}
          className="fixed inset-0 z-50 overflow-y-auto bg-slate-950/80 backdrop-blur-xs flex items-center justify-center p-4"
        >
          <div
            onClick={e => e.stopPropagation()}
            className="bg-white rounded-2xl max-w-2xl w-full p-4 space-y-3 shadow-2xl"
          >
            <div className="flex justify-between items-center border-b border-slate-200 pb-2">
              <h3 className="font-bold text-sm text-slate-800">{previewDoc.title}</h3>
              <button onClick={() => setPreviewDoc(null)} className="text-slate-400 hover:text-slate-800">
                ✕
              </button>
            </div>
            <div className="max-h-[70vh] overflow-auto flex items-center justify-center bg-slate-50 rounded-xl p-2">
              <img src={previewDoc.imageUri} alt="" className="max-h-[65vh] object-contain rounded-lg" />
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
