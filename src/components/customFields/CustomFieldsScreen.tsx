import React, { useState } from 'react';
import {
  Sparkles,
  Plus,
  Trash2,
  Edit2,
  Code,
  Sliders,
  CheckCircle2,
  ArrowRight,
  HelpCircle,
  Play,
  User,
  ListFilter
} from 'lucide-react';
import { useApp } from '../../context/AppContext';
import { CustomField, FormulaRule, FieldType, Operator } from '../../types';
import { FormulaEvaluator } from '../../utils/formulaEvaluator';
import { toBanglaDigits } from '../../utils/banglaUtils';

export const CustomFieldsScreen: React.FC = () => {
  const {
    customFields,
    formulaRules,
    students,
    addCustomField,
    updateCustomField,
    deleteCustomField,
    addFormulaRule,
    deleteFormulaRule,
    showToast
  } = useApp();

  const [activeTab, setActiveTab] = useState<'fields' | 'formulas' | 'tester'>('fields');

  // Custom Field Form
  const [isAddingField, setIsAddingField] = useState(false);
  const [fieldName, setFieldName] = useState('');
  const [fieldType, setFieldType] = useState<FieldType>('Text');
  const [fieldOptions, setFieldOptions] = useState('');
  const [isCalculated, setIsCalculated] = useState(false);

  // Formula Rule Form
  const [isAddingRule, setIsAddingRule] = useState(false);
  const [ruleTargetFieldId, setRuleTargetFieldId] = useState(customFields[0]?.id || 'category');
  const [ruleSourceField, setRuleSourceField] = useState('village');
  const [ruleOperator, setRuleOperator] = useState<Operator>('Equals');
  const [ruleValue, setRuleValue] = useState('');
  const [ruleOutputValue, setRuleOutputValue] = useState('');

  // Tester state
  const [testStudentId, setTestStudentId] = useState(students[0]?.id || '');
  const testStudent = students.find(s => s.id === testStudentId) || students[0];

  const handleSaveField = (e: React.FormEvent) => {
    e.preventDefault();
    if (!fieldName.trim()) return;

    addCustomField({
      name: fieldName,
      fieldType,
      options: fieldType === 'Dropdown' ? fieldOptions.split(',').map(s => s.trim()).filter(Boolean) : [],
      isCalculated,
      formula: isCalculated ? `RULE_BASED_${Date.now()}` : undefined
    });

    setIsAddingField(false);
    setFieldName('');
    setFieldOptions('');
    setIsCalculated(false);
  };

  const handleSaveRule = (e: React.FormEvent) => {
    e.preventDefault();
    if (!ruleValue.trim() || !ruleOutputValue.trim()) return;

    addFormulaRule({
      targetFieldId: ruleTargetFieldId,
      sourceField: ruleSourceField,
      operator: ruleOperator,
      value: ruleValue,
      outputValue: ruleOutputValue,
      priority: formulaRules.length + 1
    });

    setIsAddingRule(false);
    setRuleValue('');
    setRuleOutputValue('');
  };

  const sourceFieldOptions = [
    { label: 'গ্রাম (village)', value: 'village' },
    { label: 'শ্রেণি (studentClass)', value: 'studentClass' },
    { label: 'লিঙ্গ (gender)', value: 'gender' },
    { label: 'শাখা (section)', value: 'section' },
    { label: 'রোল নম্বর (rollNumber)', value: 'rollNumber' },
    { label: 'বয়স / বছর (age)', value: 'age' },
    { label: 'পিতার নাম (fatherName)', value: 'fatherName' },
    { label: 'মাতার নাম (motherName)', value: 'motherName' },
    { label: 'বিশেষ চাহিদা সম্পন্ন (isSpecialNeeds)', value: 'isSpecialNeeds' }
  ];

  return (
    <div className="space-y-6 pb-16">
      {/* Header */}
      <div className="bg-white p-5 rounded-2xl border border-slate-200/80 shadow-xs flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <h1 className="text-xl font-bold text-slate-900 font-serif-bn">
              কাস্টম ফিল্ড ও ফর্মুলা ইঞ্জিন
            </h1>
            <span className="text-xs px-2.5 py-0.5 rounded-full bg-indigo-100 text-indigo-800 font-bold">
              ডায়নামিক লজিক
            </span>
          </div>
          <p className="text-xs text-slate-500 mt-0.5">
            শিক্ষার্থী তথ্যে নিজস্ব ফিল্ড যুক্ত করুন ও সূত্রের সাহায্যে স্বয়ংক্রিয় মান গণনা করুন
          </p>
        </div>

        <div className="flex items-center gap-2">
          {activeTab === 'fields' && (
            <button
              onClick={() => setIsAddingField(true)}
              className="inline-flex items-center gap-1.5 px-4 py-2 rounded-lg bg-emerald-600 hover:bg-emerald-700 text-white text-xs font-bold shadow-md shadow-emerald-600/20 cursor-pointer"
            >
              <Plus className="w-4 h-4" />
              <span>নতুন কাস্টম ফিল্ড</span>
            </button>
          )}

          {activeTab === 'formulas' && (
            <button
              onClick={() => setIsAddingRule(true)}
              className="inline-flex items-center gap-1.5 px-4 py-2 rounded-lg bg-indigo-600 hover:bg-indigo-700 text-white text-xs font-bold shadow-md shadow-indigo-600/20 cursor-pointer"
            >
              <Plus className="w-4 h-4" />
              <span>নতুন ফর্মুলা রুল</span>
            </button>
          )}
        </div>
      </div>

      {/* Tabs */}
      <div className="flex border-b border-slate-200 bg-white px-5 rounded-2xl border shadow-xs gap-6">
        <button
          onClick={() => setActiveTab('fields')}
          className={`py-3.5 text-xs font-bold transition-all border-b-2 cursor-pointer flex items-center gap-2 ${
            activeTab === 'fields'
              ? 'border-emerald-600 text-emerald-700'
              : 'border-transparent text-slate-500 hover:text-slate-800'
          }`}
        >
          <Sliders className="w-4 h-4" />
          <span>কাস্টম ফিল্ডসমূহ ({toBanglaDigits(customFields.length)})</span>
        </button>

        <button
          onClick={() => setActiveTab('formulas')}
          className={`py-3.5 text-xs font-bold transition-all border-b-2 cursor-pointer flex items-center gap-2 ${
            activeTab === 'formulas'
              ? 'border-indigo-600 text-indigo-700'
              : 'border-transparent text-slate-500 hover:text-slate-800'
          }`}
        >
          <Code className="w-4 h-4" />
          <span>ফর্মুলা ও লজিক রুলস ({toBanglaDigits(formulaRules.length)})</span>
        </button>

        <button
          onClick={() => setActiveTab('tester')}
          className={`py-3.5 text-xs font-bold transition-all border-b-2 cursor-pointer flex items-center gap-2 ${
            activeTab === 'tester'
              ? 'border-sky-600 text-sky-700'
              : 'border-transparent text-slate-500 hover:text-slate-800'
          }`}
        >
          <Play className="w-4 h-4" />
          <span>লাইভ রুল টেস্টার / সিমুলেটর</span>
        </button>
      </div>

      {/* TAB 1: CUSTOM FIELDS */}
      {activeTab === 'fields' && (
        <div className="space-y-4">
          {customFields.length === 0 ? (
            <div className="bg-white p-12 rounded-2xl border border-slate-200 text-center space-y-3">
              <Sliders className="w-12 h-12 text-slate-300 mx-auto" />
              <h3 className="text-base font-bold text-slate-700">কোন কাস্টম ফিল্ড নেই</h3>
              <p className="text-xs text-slate-500 max-w-sm mx-auto">
                উপবৃত্তি যোগ্যতা, রক্তের গ্রুপ, বা বিশেষ সুবিধা ইত্যাদি সংরক্ষণে নতুন ফিল্ড তৈরি করুন।
              </p>
              <button
                onClick={() => setIsAddingField(true)}
                className="px-4 py-2 text-xs font-semibold rounded-lg bg-emerald-600 text-white"
              >
                প্রথম ফিল্ড তৈরি করুন
              </button>
            </div>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
              {customFields.map(field => (
                <div
                  key={field.id}
                  className="bg-white rounded-2xl border border-slate-200/80 p-5 shadow-xs space-y-3 relative group hover:border-emerald-300 transition-all"
                >
                  <div className="flex items-start justify-between">
                    <div>
                      <h3 className="font-bold text-sm text-slate-900">{field.name}</h3>
                      <span className="text-[11px] text-slate-400 font-mono">আইডি: {field.id}</span>
                    </div>
                    <span
                      className={`text-[10px] px-2 py-0.5 rounded-full font-bold ${
                        field.isCalculated
                          ? 'bg-indigo-50 text-indigo-700 border border-indigo-200'
                          : 'bg-slate-100 text-slate-700'
                      }`}
                    >
                      {field.isCalculated ? 'ফর্মুলা ভিত্তিক' : field.fieldType}
                    </span>
                  </div>

                  {field.options && field.options.length > 0 && (
                    <div className="space-y-1">
                      <span className="text-[10px] text-slate-400 block font-semibold">অপশনসমূহ:</span>
                      <div className="flex flex-wrap gap-1">
                        {field.options.map(opt => (
                          <span
                            key={opt}
                            className="text-[10px] bg-slate-100 text-slate-700 px-2 py-0.5 rounded-md"
                          >
                            {opt}
                          </span>
                        ))}
                      </div>
                    </div>
                  )}

                  <div className="flex items-center justify-between pt-2 border-t border-slate-100">
                    <span className="text-[11px] text-slate-500">
                      {field.isCalculated
                        ? `${formulaRules.filter(r => r.targetFieldId === field.id).length} টি রুল সংযুক্ত`
                        : 'ম্যানুয়াল ইনপুট'}
                    </span>
                    <button
                      onClick={() => deleteCustomField(field.id)}
                      className="p-1.5 rounded-lg text-rose-500 hover:bg-rose-50 transition-colors"
                    >
                      <Trash2 className="w-4 h-4" />
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* TAB 2: FORMULA RULES */}
      {activeTab === 'formulas' && (
        <div className="space-y-4">
          <div className="bg-indigo-50/70 border border-indigo-200/80 p-4 rounded-2xl text-xs text-indigo-900 space-y-1">
            <h4 className="font-bold flex items-center gap-1.5">
              <Sparkles className="w-4 h-4 text-indigo-600" />
              <span>ফর্মুলা রুলস কীভাবে কাজ করে?</span>
            </h4>
            <p className="text-indigo-800 leading-relaxed">
              একটি ফর্মুলা রুল শিক্ষার্থীর যেকোনো উৎস তথ্যের (যেমন: গ্রাম, শ্রেণি, লিঙ্গ বা বয়স) শর্ত যাচাই করে স্বয়ংক্রিয়ভাবে ফলাফল (যেমন: অভ্যন্তরীণ/বহিরাগত, উপবৃত্তি যোগ্য) নির্ধারণ করে।
            </p>
          </div>

          <div className="bg-white rounded-2xl border border-slate-200/80 shadow-xs overflow-hidden">
            <table className="w-full text-left text-xs">
              <thead className="bg-slate-50 text-slate-600 font-bold border-b border-slate-200 uppercase text-[11px]">
                <tr>
                  <th className="p-3.5">টার্গেট ফিল্ড</th>
                  <th className="p-3.5">উৎস তথ্য (Source)</th>
                  <th className="p-3.5">অপারেটর</th>
                  <th className="p-3.5">শর্ত মান (Condition)</th>
                  <th className="p-3.5">ফলাফল মান (Output)</th>
                  <th className="p-3.5 text-right">মুছুন</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {formulaRules.map(rule => {
                  const targetF = customFields.find(f => f.id === rule.targetFieldId);
                  return (
                    <tr key={rule.id} className="hover:bg-slate-50">
                      <td className="p-3.5 font-bold text-slate-800">
                        {targetF ? targetF.name : rule.targetFieldId}
                      </td>
                      <td className="p-3.5 font-mono text-slate-700">{rule.sourceField}</td>
                      <td className="p-3.5">
                        <span className="px-2 py-0.5 rounded-full bg-slate-100 text-slate-700 font-bold text-[10px]">
                          {rule.operator}
                        </span>
                      </td>
                      <td className="p-3.5 font-semibold text-emerald-800">{rule.value}</td>
                      <td className="p-3.5 font-bold text-indigo-700">{rule.outputValue}</td>
                      <td className="p-3.5 text-right">
                        <button
                          onClick={() => deleteFormulaRule(rule.id)}
                          className="p-1.5 text-rose-500 hover:bg-rose-50 rounded-lg"
                        >
                          <Trash2 className="w-4 h-4" />
                        </button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* TAB 3: LIVE TESTER */}
      {activeTab === 'tester' && (
        <div className="bg-white p-6 rounded-2xl border border-slate-200/80 shadow-xs space-y-6">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-b border-slate-100 pb-4">
            <div>
              <h3 className="font-bold text-sm text-slate-900 font-serif-bn">
                শিক্ষার্থী দিয়ে লাইভ সিমুলেশন
              </h3>
              <p className="text-xs text-slate-500">
                যেকোনো শিক্ষার্থী নির্বাচন করে তার উপর সকল কাস্টম ফিল্ড ও সূত্রের কার্যকারিতা পরীক্ষা করুন
              </p>
            </div>

            <select
              value={testStudentId}
              onChange={e => setTestStudentId(e.target.value)}
              className="px-3 py-2 text-xs rounded-xl border border-slate-300 bg-slate-50 font-bold text-slate-800"
            >
              {students.map(s => (
                <option key={s.id} value={s.id}>
                  {s.name} ({s.studentClass}, গ্রাম: {s.village || '—'})
                </option>
              ))}
            </select>
          </div>

          {testStudent && (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              {/* Student Base Data */}
              <div className="p-4 rounded-xl bg-slate-50 border border-slate-200 space-y-2 text-xs">
                <h4 className="font-bold text-slate-700 uppercase tracking-wider text-[11px]">
                  শিক্ষার্থীর মূল তথ্য
                </h4>
                <div className="grid grid-cols-2 gap-2">
                  <div>নাম: <strong>{testStudent.name}</strong></div>
                  <div>শ্রেণি: <strong>{testStudent.studentClass}</strong></div>
                  <div>রোল: <strong>{toBanglaDigits(testStudent.rollNumber)}</strong></div>
                  <div>লিঙ্গ: <strong>{testStudent.gender}</strong></div>
                  <div>গ্রাম: <strong>{testStudent.village || '—'}</strong></div>
                  <div>বিশেষ চাহিদা: <strong>{testStudent.isSpecialNeeds ? 'হ্যাঁ' : 'না'}</strong></div>
                </div>
              </div>

              {/* Formula Outputs */}
              <div className="p-4 rounded-xl bg-indigo-50/60 border border-indigo-200 space-y-2 text-xs">
                <h4 className="font-bold text-indigo-900 uppercase tracking-wider text-[11px] flex items-center gap-1">
                  <Sparkles className="w-3.5 h-3.5 text-indigo-600" />
                  <span>ফর্মুলা ও কাস্টম ফিল্ড মূল্যায়ন</span>
                </h4>
                <div className="space-y-2">
                  {customFields.map(f => {
                    const evaluated = FormulaEvaluator.getFieldValue(
                      testStudent,
                      f.id,
                      customFields,
                      formulaRules
                    );
                    return (
                      <div key={f.id} className="p-2 bg-white rounded-lg border border-indigo-100 flex justify-between items-center">
                        <span className="font-semibold text-slate-700">{f.name}:</span>
                        <span className="font-bold text-indigo-700">{evaluated || '—'}</span>
                      </div>
                    );
                  })}
                </div>
              </div>
            </div>
          )}
        </div>
      )}

      {/* Add Custom Field Modal */}
      {isAddingField && (
        <div className="fixed inset-0 z-50 overflow-y-auto bg-slate-900/60 backdrop-blur-xs flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl shadow-2xl max-w-md w-full p-6 space-y-4 animate-in fade-in zoom-in-95">
            <h3 className="font-bold text-slate-900 text-sm font-serif-bn border-b border-slate-100 pb-2">
              নতুন কাস্টম ফিল্ড যুক্ত করুন
            </h3>

            <form onSubmit={handleSaveField} className="space-y-4 text-xs">
              <div>
                <label className="block font-semibold text-slate-700 mb-1">ফিল্ডের নাম</label>
                <input
                  type="text"
                  required
                  placeholder="যেমন: রক্তের গ্রুপ / উপবৃত্তি স্ট্যাটাস"
                  value={fieldName}
                  onChange={e => setFieldName(e.target.value)}
                  className="w-full px-3 py-2 rounded-lg border border-slate-300 bg-slate-50"
                />
              </div>

              <div>
                <label className="block font-semibold text-slate-700 mb-1">ফিল্ডের ধরন</label>
                <select
                  value={fieldType}
                  onChange={e => setFieldType(e.target.value as FieldType)}
                  className="w-full px-3 py-2 rounded-lg border border-slate-300 bg-slate-50"
                >
                  <option value="Text">টেক্সট (Text)</option>
                  <option value="Number">সংখ্যা (Number)</option>
                  <option value="Dropdown">ড্রপডাউন তালিকা (Dropdown)</option>
                  <option value="Date">তারিখ (Date)</option>
                </select>
              </div>

              {fieldType === 'Dropdown' && (
                <div>
                  <label className="block font-semibold text-slate-700 mb-1">
                    অপশনসমূহ (কমা দিয়ে আলাদা করুন)
                  </label>
                  <input
                    type="text"
                    placeholder="যেমন: A+, B+, AB+, O+"
                    value={fieldOptions}
                    onChange={e => setFieldOptions(e.target.value)}
                    className="w-full px-3 py-2 rounded-lg border border-slate-300 bg-slate-50"
                  />
                </div>
              )}

              <div className="flex items-center gap-2 p-3 bg-slate-50 rounded-xl border border-slate-200">
                <input
                  type="checkbox"
                  id="isCalculatedCheck"
                  checked={isCalculated}
                  onChange={e => setIsCalculated(e.target.checked)}
                  className="rounded-sm text-emerald-600"
                />
                <label htmlFor="isCalculatedCheck" className="font-semibold text-slate-700 cursor-pointer">
                  এটি ফর্মুলা/শর্তের মাধ্যমে স্বয়ংক্রিয় গণনা হবে
                </label>
              </div>

              <div className="flex justify-end gap-2 pt-3 border-t border-slate-100">
                <button
                  type="button"
                  onClick={() => setIsAddingField(false)}
                  className="px-4 py-2 rounded-lg border border-slate-300 text-slate-700 font-medium cursor-pointer"
                >
                  বাতিল
                </button>
                <button
                  type="submit"
                  className="px-4 py-2 rounded-lg bg-emerald-600 hover:bg-emerald-700 text-white font-bold cursor-pointer"
                >
                  সংরক্ষণ করুন
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Add Formula Rule Modal */}
      {isAddingRule && (
        <div className="fixed inset-0 z-50 overflow-y-auto bg-slate-900/60 backdrop-blur-xs flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl shadow-2xl max-w-md w-full p-6 space-y-4 animate-in fade-in zoom-in-95">
            <h3 className="font-bold text-slate-900 text-sm font-serif-bn border-b border-slate-100 pb-2">
              নতুন ফর্মুলা রুল নির্ধারণ করুন
            </h3>

            <form onSubmit={handleSaveRule} className="space-y-4 text-xs">
              <div>
                <label className="block font-semibold text-slate-700 mb-1">টার্গেট ফিল্ড</label>
                <select
                  value={ruleTargetFieldId}
                  onChange={e => setRuleTargetFieldId(e.target.value)}
                  className="w-full px-3 py-2 rounded-lg border border-slate-300 bg-slate-50"
                >
                  {customFields.map(f => (
                    <option key={f.id} value={f.id}>
                      {f.name}
                    </option>
                  ))}
                </select>
              </div>

              <div>
                <label className="block font-semibold text-slate-700 mb-1">উৎস তথ্য (Source Property)</label>
                <select
                  value={ruleSourceField}
                  onChange={e => setRuleSourceField(e.target.value)}
                  className="w-full px-3 py-2 rounded-lg border border-slate-300 bg-slate-50"
                >
                  {sourceFieldOptions.map(opt => (
                    <option key={opt.value} value={opt.value}>
                      {opt.label}
                    </option>
                  ))}
                </select>
              </div>

              <div>
                <label className="block font-semibold text-slate-700 mb-1">শর্তের অপারেটর</label>
                <select
                  value={ruleOperator}
                  onChange={e => setRuleOperator(e.target.value as Operator)}
                  className="w-full px-3 py-2 rounded-lg border border-slate-300 bg-slate-50"
                >
                  <option value="Equals">হুবহু সমান (Equals)</option>
                  <option value="NotEquals">সমান নয় (Not Equals)</option>
                  <option value="Contains">শব্দ রয়েছে (Contains)</option>
                  <option value="InList">তালিকার অন্তর্ভুক্ত (In List - কমা পৃথক)</option>
                  <option value="GreaterThan">বড় / বেশি (Greater Than)</option>
                  <option value="LessThan">ছোট / কম (Less Than)</option>
                </select>
              </div>

              <div>
                <label className="block font-semibold text-slate-700 mb-1">শর্ত মান (Condition Value)</label>
                <input
                  type="text"
                  required
                  placeholder="যেমন: পশ্চিম রামপুর"
                  value={ruleValue}
                  onChange={e => setRuleValue(e.target.value)}
                  className="w-full px-3 py-2 rounded-lg border border-slate-300 bg-slate-50"
                />
              </div>

              <div>
                <label className="block font-semibold text-slate-700 mb-1">ফলাফল মান (Output Value)</label>
                <input
                  type="text"
                  required
                  placeholder="যেমন: অভ্যন্তরীণ / উপবৃত্তি পাবে"
                  value={ruleOutputValue}
                  onChange={e => setRuleOutputValue(e.target.value)}
                  className="w-full px-3 py-2 rounded-lg border border-slate-300 bg-slate-50"
                />
              </div>

              <div className="flex justify-end gap-2 pt-3 border-t border-slate-100">
                <button
                  type="button"
                  onClick={() => setIsAddingRule(false)}
                  className="px-4 py-2 rounded-lg border border-slate-300 text-slate-700 font-medium cursor-pointer"
                >
                  বাতিল
                </button>
                <button
                  type="submit"
                  className="px-4 py-2 rounded-lg bg-indigo-600 hover:bg-indigo-700 text-white font-bold cursor-pointer"
                >
                  রুল যোগ করুন
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
