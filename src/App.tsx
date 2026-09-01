import React, { useState } from 'react';
import { AppProvider, useApp } from './context/AppContext';
import { Navbar } from './components/layout/Navbar';
import { NotificationToast } from './components/layout/NotificationToast';
import { DashboardScreen } from './components/dashboard/DashboardScreen';
import { StudentScreen } from './components/students/StudentScreen';
import { ToolsHubScreen } from './components/tools/ToolsHubScreen';
import { CustomFieldsScreen } from './components/customFields/CustomFieldsScreen';
import { SettingsScreen } from './components/settings/SettingsScreen';
import { StudentFormModal } from './components/students/StudentFormModal';
import { Student } from './types';

const MainLayout: React.FC = () => {
  const { activeTab, setActiveTab, setFilterClass } = useApp();
  const [isAddStudentOpen, setIsAddStudentOpen] = useState(false);
  const [selectedStudentForView, setSelectedStudentForView] = useState<Student | null>(null);
  const [initialToolId, setInitialToolId] = useState<any>('hub');

  const handleNavigateToStudents = (filterCls?: string) => {
    if (filterCls) {
      setFilterClass(filterCls);
    }
    setActiveTab('students');
  };

  const handleNavigateToTool = (toolId: string) => {
    setInitialToolId(toolId);
    setActiveTab('tools');
  };

  return (
    <div className="min-h-screen bg-slate-100/70 flex flex-col text-slate-900 font-sans antialiased">
      {/* Top Main Navbar */}
      <Navbar onOpenAddStudent={() => setIsAddStudentOpen(true)} />

      {/* Main Content Area */}
      <main className="flex-1 max-w-7xl w-full mx-auto px-4 sm:px-6 lg:px-8 py-6">
        {activeTab === 'dashboard' && (
          <DashboardScreen
            onNavigateToStudents={handleNavigateToStudents}
            onNavigateToTool={handleNavigateToTool}
            onOpenAddStudent={() => setIsAddStudentOpen(true)}
          />
        )}

        {activeTab === 'students' && (
          <StudentScreen
            onOpenAddStudent={() => setIsAddStudentOpen(true)}
            selectedStudentFromDash={selectedStudentForView}
          />
        )}

        {activeTab === 'tools' && (
          <ToolsHubScreen initialTool={initialToolId} />
        )}

        {activeTab === 'custom-fields' && (
          <CustomFieldsScreen />
        )}

        {activeTab === 'settings' && (
          <SettingsScreen />
        )}
      </main>

      {/* Global Add Student Modal */}
      <StudentFormModal
        isOpen={isAddStudentOpen}
        onClose={() => setIsAddStudentOpen(false)}
      />

      {/* Global Notification Toast */}
      <NotificationToast />
    </div>
  );
};

export function App() {
  return (
    <AppProvider>
      <MainLayout />
    </AppProvider>
  );
}

export default App;
