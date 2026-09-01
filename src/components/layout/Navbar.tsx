import React from 'react';
import {
  GraduationCap,
  LayoutDashboard,
  Users,
  Wrench,
  Sparkles,
  Settings,
  School,
  FileText,
  Search,
  Plus
} from 'lucide-react';
import { useApp } from '../../context/AppContext';
import { ActiveScreen } from '../../types';
import { toBanglaDigits } from '../../utils/banglaUtils';

export const Navbar: React.FC<{ onOpenAddStudent: () => void }> = ({ onOpenAddStudent }) => {
  const {
    activeScreen,
    setActiveScreen,
    setActiveTool,
    schoolInfo,
    searchQuery,
    setSearchQuery,
    students
  } = useApp();

  const navItems: { id: ActiveScreen; label: string; icon: React.ReactNode; badge?: string }[] = [
    {
      id: 'dashboard',
      label: 'ড্যাশবোর্ড',
      icon: <LayoutDashboard className="w-4 h-4" />
    },
    {
      id: 'students',
      label: 'শিক্ষার্থী তালিকা',
      icon: <Users className="w-4 h-4" />,
      badge: toBanglaDigits(students.length)
    },
    {
      id: 'tools_hub',
      label: 'টুলস হাব',
      icon: <Wrench className="w-4 h-4" />
    },
    {
      id: 'custom_fields',
      label: 'ফিল্ড ও সূত্র',
      icon: <Sparkles className="w-4 h-4" />
    },
    {
      id: 'settings',
      label: 'সেটিংস',
      icon: <Settings className="w-4 h-4" />
    }
  ];

  const handleNavClick = (screen: ActiveScreen) => {
    setActiveScreen(screen);
    setActiveTool(null);
  };

  return (
    <header className="sticky top-0 z-40 bg-white/95 backdrop-blur-md border-b border-slate-200 shadow-xs">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex items-center justify-between h-16 gap-4">
          {/* Logo and School Name */}
          <div className="flex items-center gap-3 min-w-0">
            <div
              onClick={() => handleNavClick('dashboard')}
              className="flex items-center gap-2.5 cursor-pointer group"
            >
              <div className="w-10 h-10 rounded-xl bg-gradient-to-tr from-emerald-600 to-teal-500 flex items-center justify-center text-white shadow-md shadow-emerald-500/20 group-hover:scale-105 transition-transform">
                <GraduationCap className="w-6 h-6" />
              </div>
              <div className="hidden sm:block">
                <div className="flex items-center gap-2">
                  <span className="font-bold text-lg tracking-tight bg-gradient-to-r from-emerald-700 to-teal-700 bg-clip-text text-transparent">
                    অন্বেষা
                  </span>
                  <span className="text-xs px-1.5 py-0.5 rounded-full bg-emerald-50 text-emerald-700 font-semibold border border-emerald-200/60">
                    v৩.০
                  </span>
                </div>
                <p className="text-xs text-slate-500 truncate max-w-[220px] md:max-w-xs" title={schoolInfo.schoolName}>
                  {schoolInfo.schoolName}
                </p>
              </div>
            </div>
          </div>

          {/* Quick Search */}
          <div className="flex-1 max-w-md hidden md:block">
            <div className="relative">
              <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
              <input
                type="text"
                value={searchQuery}
                onChange={e => {
                  setSearchQuery(e.target.value);
                  if (activeScreen !== 'students') {
                    setActiveScreen('students');
                  }
                }}
                placeholder="নাম, রোল, মোবাইল বা জন্ম নিবন্ধন নং দিয়ে খুঁজুন..."
                className="w-full pl-9 pr-4 py-1.5 text-sm rounded-lg bg-slate-100/80 border border-slate-200 focus:bg-white focus:border-emerald-500 focus:ring-2 focus:ring-emerald-500/20 outline-hidden transition-all"
              />
              {searchQuery && (
                <button
                  onClick={() => setSearchQuery('')}
                  className="absolute right-2.5 top-1/2 -translate-y-1/2 text-xs text-slate-400 hover:text-slate-600 bg-slate-200 rounded-full w-4 h-4 flex items-center justify-center"
                >
                  ×
                </button>
              )}
            </div>
          </div>

          {/* Action Button */}
          <div className="flex items-center gap-2">
            <button
              onClick={onOpenAddStudent}
              className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-lg bg-emerald-600 hover:bg-emerald-700 text-white text-sm font-medium shadow-sm shadow-emerald-600/20 transition-colors cursor-pointer"
            >
              <Plus className="w-4 h-4" />
              <span className="hidden sm:inline">নতুন শিক্ষার্থী</span>
            </button>
          </div>
        </div>

        {/* Navigation Tabs */}
        <nav className="flex space-x-1 overflow-x-auto pb-1 scrollbar-none border-t border-slate-100 pt-1">
          {navItems.map(item => {
            const isActive = activeScreen === item.id;
            return (
              <button
                key={item.id}
                onClick={() => handleNavClick(item.id)}
                className={`flex items-center gap-2 px-3.5 py-2 rounded-md text-sm font-medium whitespace-nowrap transition-all cursor-pointer ${
                  isActive
                    ? 'bg-emerald-50 text-emerald-700 border-b-2 border-emerald-600 font-semibold'
                    : 'text-slate-600 hover:text-slate-900 hover:bg-slate-100/60'
                }`}
              >
                {item.icon}
                <span>{item.label}</span>
                {item.badge && (
                  <span
                    className={`ml-1 text-xs px-1.5 py-0.2 rounded-full ${
                      isActive ? 'bg-emerald-200/70 text-emerald-800' : 'bg-slate-200 text-slate-700'
                    }`}
                  >
                    {item.badge}
                  </span>
                )}
              </button>
            );
          })}
        </nav>
      </div>
    </header>
  );
};
