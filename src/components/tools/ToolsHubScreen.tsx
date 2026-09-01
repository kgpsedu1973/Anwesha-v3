import React, { useState } from 'react';
import {
  UserCheck,
  Calculator,
  IdCard,
  LayoutGrid,
  Award,
  FileText,
  Sparkles,
  ArrowRight,
  Printer,
  ChevronRight,
  BookOpen,
  Calendar
} from 'lucide-react';
import { AttendanceReportTool } from './AttendanceReportTool';
import { AgeCalculatorTool } from './AgeCalculatorTool';
import { AdmitCardMakerTool } from './AdmitCardMakerTool';
import { SeatPlanMakerTool } from './SeatPlanMakerTool';
import { CertificateMakerTool } from './CertificateMakerTool';
import { DocumentScannerTool } from './DocumentScannerTool';

type ToolId = 'hub' | 'attendance' | 'age-calc' | 'admit-card' | 'seat-plan' | 'certificate' | 'scanner';

export const ToolsHubScreen: React.FC<{ initialTool?: ToolId }> = ({ initialTool = 'hub' }) => {
  const [activeTool, setActiveTool] = useState<ToolId>(initialTool);

  if (activeTool === 'attendance') {
    return <AttendanceReportTool onBack={() => setActiveTool('hub')} />;
  }
  if (activeTool === 'age-calc') {
    return <AgeCalculatorTool onBack={() => setActiveTool('hub')} />;
  }
  if (activeTool === 'admit-card') {
    return <AdmitCardMakerTool onBack={() => setActiveTool('hub')} />;
  }
  if (activeTool === 'seat-plan') {
    return <SeatPlanMakerTool onBack={() => setActiveTool('hub')} />;
  }
  if (activeTool === 'certificate') {
    return <CertificateMakerTool onBack={() => setActiveTool('hub')} />;
  }
  if (activeTool === 'scanner') {
    return <DocumentScannerTool onBack={() => setActiveTool('hub')} />;
  }

  const toolCards = [
    {
      id: 'attendance' as ToolId,
      title: 'হাজিরা ও মাসিক উপস্থিতি খাতা',
      desc: 'শ্রেণিভিত্তিক দৈনিক ডিজিটাল হাজিরা এবং স্বয়ংক্রিয় গড় শতকরাসহ মাসিক উপস্থিতি বিবরণী রিপোর্ট।',
      icon: UserCheck,
      color: 'from-emerald-500 to-teal-600',
      badge: 'দৈনিক ও মাসিক'
    },
    {
      id: 'age-calc' as ToolId,
      title: 'স্মার্ট বয়স ক্যালকুলেটর',
      desc: 'ভর্তি বয়স যাচাই, দিন অন্তর্ভুক্তি সমন্বয় এবং জন্মদিনের কাউন্টডাউন সহ লাইভ বয়স ক্যালকুলেশন।',
      icon: Calculator,
      color: 'from-sky-500 to-blue-600',
      badge: 'লাইভ বয়স'
    },
    {
      id: 'admit-card' as ToolId,
      title: 'প্রবেশপত্র (Admit Card) মেকার',
      desc: 'পরীক্ষার রুটিনসহ শ্রেণির সকল শিক্ষার্থীর জন্য এক ক্লিকে প্রিন্টযোগ্য প্রাতিষ্ঠানিক এডমিট কার্ড তৈরি।',
      icon: IdCard,
      color: 'from-emerald-600 to-emerald-800',
      badge: 'ব্যাচ প্রিন্ট'
    },
    {
      id: 'seat-plan' as ToolId,
      title: 'সিট প্ল্যান ও বেঞ্চ স্টিকার মেকার',
      desc: 'পরীক্ষার হল ও কক্ষভিত্তিক আসন বণ্টন তালিকা এবং বেঞ্চে লাগানোর প্রিন্টযোগ্য স্টিকার শিট।',
      icon: LayoutGrid,
      color: 'from-indigo-500 to-indigo-700',
      badge: 'কক্ষ বিন্যাস'
    },
    {
      id: 'certificate' as ToolId,
      title: 'প্রত্যয়নপত্র ও সনদপত্র মেকার',
      desc: 'অধ্যয়নরত প্রত্যয়ন, চারিত্রিক প্রশংসাপত্র, বয়স সনদ ও ছাড়পত্র (TC) অফিসিয়াল প্যাডে প্রিন্ট করুন।',
      icon: Award,
      color: 'from-amber-500 to-amber-700',
      badge: 'অফিসিয়াল সনদ'
    },
    {
      id: 'scanner' as ToolId,
      title: 'ডকুমেন্ট স্ক্যানার ও ডিজিটাল আর্কাইভ',
      desc: 'শিক্ষার্থীদের জন্ম নিবন্ধন সনদ, অভিভাবকদের NID, নম্বরপত্র এবং সনদপত্রের ডিজিটাল ফাইল সংগ্রহ।',
      icon: FileText,
      color: 'from-teal-500 to-teal-700',
      badge: 'ডিজিটাল ফাইল'
    }
  ];

  return (
    <div className="space-y-6 pb-16">
      {/* Top Banner */}
      <div className="bg-gradient-to-r from-emerald-800 via-teal-800 to-slate-900 text-white p-6 sm:p-8 rounded-2xl shadow-lg relative overflow-hidden space-y-2">
        <div className="flex items-center gap-2">
          <Sparkles className="w-5 h-5 text-emerald-300" />
          <span className="text-xs uppercase font-bold tracking-wider text-emerald-200">
            বিদ্যালয় ব্যবস্থাপনা টুলকিট
          </span>
        </div>
        <h1 className="text-2xl sm:text-3xl font-bold font-serif-bn">
          স্মার্ট স্কুল এডমিনিস্ট্রেশন টুলস
        </h1>
        <p className="text-xs sm:text-sm text-emerald-100/90 max-w-xl">
          দৈনন্দিন প্রাতিষ্ঠানিক কার্যাবলি দ্রুত ও নির্ভুলভাবে সম্পন্ন করার জন্য প্রয়োজনীয় সকল অফিশিয়াল টুল
        </p>
      </div>

      {/* Grid of Tools */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
        {toolCards.map(tool => {
          const Icon = tool.icon;
          return (
            <div
              key={tool.id}
              onClick={() => setActiveTool(tool.id)}
              className="bg-white rounded-2xl border border-slate-200/80 p-6 shadow-xs hover:shadow-md hover:border-emerald-300 transition-all cursor-pointer group flex flex-col justify-between space-y-4"
            >
              <div className="space-y-3">
                <div className="flex items-center justify-between">
                  <div
                    className={`w-12 h-12 rounded-xl bg-gradient-to-br ${tool.color} text-white flex items-center justify-center shadow-md group-hover:scale-105 transition-transform`}
                  >
                    <Icon className="w-6 h-6" />
                  </div>
                  <span className="text-[11px] font-bold px-2.5 py-1 rounded-full bg-slate-100 text-slate-700">
                    {tool.badge}
                  </span>
                </div>

                <div>
                  <h3 className="font-bold text-base text-slate-900 font-serif-bn group-hover:text-emerald-700 transition-colors">
                    {tool.title}
                  </h3>
                  <p className="text-xs text-slate-500 mt-1 leading-relaxed">
                    {tool.desc}
                  </p>
                </div>
              </div>

              <div className="pt-2 border-t border-slate-100 flex items-center justify-between text-xs font-bold text-emerald-700 group-hover:text-emerald-800">
                <span>টুল ব্যবহার করুন</span>
                <ChevronRight className="w-4 h-4 group-hover:translate-x-1 transition-transform" />
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
};
