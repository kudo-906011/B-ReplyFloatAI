import React from 'react';
import { 
  Bot, 
  Power, 
  Layers, 
  Sparkles, 
  ShieldCheck, 
  CheckCircle2, 
  AlertCircle, 
  Smartphone, 
  ArrowRight, 
  Sliders, 
  AppWindow, 
  Cpu,
  RefreshCw,
  ExternalLink,
  Zap,
  ZapOff,
  MousePointerClick,
  SlidersHorizontal,
  ChevronRight
} from 'lucide-react';
import { 
  InstalledApp, 
  OverlaySettings, 
  AIProviderConfig, 
  PermissionStatus,
  ReplySettings
} from '../types';

interface HomeScreenProps {
  isServiceActive: boolean;
  setIsServiceActive: (active: boolean) => void;
  overlaySettings: OverlaySettings;
  replySettings: ReplySettings;
  activeProvider: AIProviderConfig;
  currentApp: InstalledApp;
  supportedAppsCount: number;
  permissions: PermissionStatus;
  onNavigateTab: (tab: any) => void;
  onTriggerTest: () => void;
  onRequestOverlayPermission?: () => void;
  onRequestAccessibilityPermission?: () => void;
  onRequestNotificationPermission?: () => void;
  isNativeAndroid?: boolean;
}

export const HomeScreen: React.FC<HomeScreenProps> = ({
  isServiceActive,
  setIsServiceActive,
  overlaySettings,
  replySettings,
  activeProvider,
  currentApp,
  supportedAppsCount,
  permissions,
  onNavigateTab,
  onTriggerTest,
  onRequestOverlayPermission,
  onRequestAccessibilityPermission,
  onRequestNotificationPermission,
  isNativeAndroid = false,
}) => {
  const isAuto = replySettings?.autoGenerate?.enabled ?? true;
  const providerName = activeProvider?.name || 'Google Gemini';
  const providerModel = activeProvider?.model || 'gemini-3.7-flash';
  const selectedStyle = replySettings?.selectedStyle || 'Logical';
  const suggestionCount = replySettings?.suggestionCount || 3;
  const responseLength = replySettings?.responseLength || 'normal';
  const debounceMs = replySettings?.autoGenerate?.minDelayBeforeGeneratingMs || 500;
  const transparency = Math.round(((overlaySettings?.transparency ?? 0.95)) * 100);
  const isPassThrough = overlaySettings?.passThroughMode || overlaySettings?.interactionMode === 'passthrough';

  const allPermissionsGranted = permissions.overlay && permissions.accessibility && permissions.notifications;

  const handleStartToggle = () => {
    if (!isServiceActive) {
      if (!permissions.overlay && onRequestOverlayPermission) {
        onRequestOverlayPermission();
        return;
      }
      if (!permissions.accessibility && onRequestAccessibilityPermission) {
        onRequestAccessibilityPermission();
      }
    }
    setIsServiceActive(!isServiceActive);
  };

  return (
    <div className="max-w-5xl mx-auto space-y-4">
      {/* Missing Permissions Action Banner (When on real Android or missing permissions) */}
      {!allPermissionsGranted && (
        <div className="p-4 rounded-2xl bg-[#1c1114] border border-[#ef4444] shadow-lg space-y-3">
          <div className="flex items-start justify-between gap-3">
            <div className="flex items-start space-x-3">
              <div className="w-9 h-9 rounded-xl bg-[#dc262622] border border-[#ef444455] flex items-center justify-center text-[#ef4444] shrink-0 mt-0.5">
                <AlertCircle className="w-5 h-5" />
              </div>
              <div className="space-y-1">
                <h3 className="text-sm font-bold text-white flex items-center gap-2">
                  System Setup Required to Float Over Other Apps
                </h3>
                <p className="text-xs text-[#fca5a5] leading-relaxed">
                  Android requires explicit permission for ReplyFloat AI to display over apps (WhatsApp, Reddit, Discord) and detect message text.
                </p>
              </div>
            </div>
          </div>

          <div className="flex flex-wrap gap-2 pt-1 border-t border-[#ef444433]">
            {!permissions.overlay && (
              <button
                id="grant-overlay-btn"
                onClick={onRequestOverlayPermission}
                className="px-3.5 py-2 rounded-xl bg-[#dc2626] hover:bg-[#b91c1c] text-white text-xs font-bold flex items-center space-x-1.5 transition-all shadow-sm active:scale-95"
              >
                <span>1. Grant "Display Over Other Apps"</span>
                <ChevronRight className="w-3.5 h-3.5" />
              </button>
            )}

            {!permissions.accessibility && (
              <button
                id="grant-accessibility-btn"
                onClick={onRequestAccessibilityPermission}
                className="px-3.5 py-2 rounded-xl bg-[#21262d] hover:bg-[#30363d] border border-[#ef444488] text-[#fca5a5] text-xs font-bold flex items-center space-x-1.5 transition-all shadow-sm active:scale-95"
              >
                <span>2. Enable Accessibility Context Service</span>
                <ChevronRight className="w-3.5 h-3.5" />
              </button>
            )}

            {!permissions.notifications && (
              <button
                id="grant-notifications-btn"
                onClick={onRequestNotificationPermission}
                className="px-3.5 py-2 rounded-xl bg-[#21262d] hover:bg-[#30363d] border border-[#30363d] text-[#e1e4e8] text-xs font-bold flex items-center space-x-1.5 transition-all shadow-sm active:scale-95"
              >
                <span>3. Allow Background Notification</span>
                <ChevronRight className="w-3.5 h-3.5" />
              </button>
            )}
          </div>
        </div>
      )}

      {/* Hero Master Status Banner */}
      <div className={`p-5 rounded-2xl border transition-all ${
        isServiceActive
          ? 'bg-[#0d1117] border-[#dc2626] shadow-sm'
          : 'bg-[#0d1117] border-[#30363d]'
      }`}>
        <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
          <div className="space-y-1">
            <div className="flex items-center space-x-2">
              <div className={`w-2.5 h-2.5 rounded-full ${isServiceActive ? 'bg-[#ef4444] animate-pulse' : 'bg-[#6e7681]'}`} />
              <span className="text-[11px] font-bold uppercase tracking-widest text-[#8b949e]">
                System Assistant Status
              </span>
            </div>
            <h1 className="text-xl sm:text-2xl font-bold text-[#e1e4e8] tracking-tight">
              {isServiceActive ? 'ReplyFloatAI Daemon: ACTIVE' : 'ReplyFloatAI Daemon: PAUSED'}
            </h1>
            <p className="text-xs text-[#8b949e] max-w-xl leading-relaxed">
              {isServiceActive
                ? `Floating over all active Android applications. Context inspection active (${isAuto ? 'Automatic Mode' : 'Manual Mode'}).`
                : 'Service paused. Toggle ON to start floating pill over your messaging apps.'}
            </p>
          </div>

          <div className="flex items-center space-x-2">
            <button
              id="home-master-toggle-btn"
              onClick={handleStartToggle}
              className={`px-5 py-2.5 rounded-xl font-bold text-xs flex items-center space-x-2 transition-all shadow-sm active:scale-95 ${
                isServiceActive
                  ? 'bg-[#21262d] text-[#8b949e] border border-[#30363d] hover:bg-[#30363d]'
                  : 'bg-[#dc2626] hover:bg-[#b91c1c] text-white'
              }`}
            >
              <Power className="w-4 h-4" />
              <span>{isServiceActive ? 'STOP ASSISTANT' : 'START FLOATING ASSISTANT'}</span>
            </button>
          </div>
        </div>

        {/* Live Quick Metrics Strip */}
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-2.5 mt-5 pt-4 border-t border-[#30363d]">
          <div className="bg-[#161b22] p-2.5 rounded-xl border border-[#30363d]">
            <span className="text-[10px] text-[#8b949e] uppercase font-bold tracking-wider">AI Provider</span>
            <div className="text-xs font-bold text-[#58a6ff] truncate mt-0.5">{providerName}</div>
            <span className="text-[10px] text-[#6e7681] font-mono">{providerModel}</span>
          </div>

          <div className="bg-[#161b22] p-2.5 rounded-xl border border-[#30363d]">
            <span className="text-[10px] text-[#8b949e] uppercase font-bold tracking-wider">Auto-Generation</span>
            <div className={`text-xs font-bold mt-0.5 flex items-center space-x-1 ${isAuto ? 'text-[#f87171]' : 'text-[#8b949e]'}`}>
              {isAuto ? <Zap className="w-3 h-3 text-[#ef4444]" /> : <ZapOff className="w-3 h-3 text-[#8b949e]" />}
              <span>{isAuto ? 'ACTIVE (Auto)' : 'MANUAL ONLY'}</span>
            </div>
            <span className="text-[10px] text-[#6e7681]">{debounceMs}ms Debounce</span>
          </div>

          <div className="bg-[#161b22] p-2.5 rounded-xl border border-[#30363d]">
            <span className="text-[10px] text-[#8b949e] uppercase font-bold tracking-wider">Active Reply Style</span>
            <div className="text-xs font-bold text-[#d29922] mt-0.5">{selectedStyle}</div>
            <span className="text-[10px] text-[#6e7681]">{suggestionCount} choices • {responseLength}</span>
          </div>

          <div className="bg-[#161b22] p-2.5 rounded-xl border border-[#30363d]">
            <span className="text-[10px] text-[#8b949e] uppercase font-bold tracking-wider">Overlay Opacity</span>
            <div className="text-xs font-bold text-[#e1e4e8] mt-0.5">{transparency}% Opacity</div>
            <span className="text-[10px] text-[#6e7681]">{isPassThrough ? 'Pass-Through Active' : 'Interactive Touch'}</span>
          </div>
        </div>
      </div>

      {/* Permission Checklist & Diagnostic Status */}
      <div className="bg-[#0d1117] border border-[#30363d] rounded-2xl p-4 space-y-3">
        <div className="flex items-center justify-between">
          <div className="flex items-center space-x-2">
            <ShieldCheck className="w-4 h-4 text-[#58a6ff]" />
            <h2 className="text-xs font-bold uppercase tracking-wider text-[#e1e4e8]">Android System Permissions Audit</h2>
          </div>
          <span className={`text-[10px] font-bold px-2.5 py-0.5 rounded border ${
            allPermissionsGranted
              ? 'text-[#f87171] bg-[#dc262622] border-[#dc262644]'
              : 'text-[#fca5a5] bg-[#7f1d1d33] border-[#ef444455]'
          }`}>
            {allPermissionsGranted ? 'ALL PRIVILEGES GRANTED' : 'ACTION REQUIRED'}
          </span>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-3 gap-2.5">
          {/* Overlay Permission */}
          <div 
            onClick={onRequestOverlayPermission}
            className={`p-3 bg-[#161b22] rounded-xl border space-y-1.5 transition-all cursor-pointer hover:border-[#ef4444] ${
              permissions.overlay ? 'border-[#30363d] border-l-2 border-l-[#ef4444]' : 'border-[#ef4444] bg-[#1c1114]'
            }`}
          >
            <div className="flex items-center justify-between">
              <span className="text-xs font-semibold text-[#e1e4e8]">Display Over Apps</span>
              {permissions.overlay ? (
                <CheckCircle2 className="w-3.5 h-3.5 text-[#ef4444]" />
              ) : (
                <span className="text-[10px] font-bold text-white bg-[#dc2626] px-1.5 py-0.5 rounded">GRANT</span>
              )}
            </div>
            <p className="text-[11px] text-[#8b949e] leading-snug">
              Allows floating reply pill to appear above WhatsApp, Telegram, Reddit, and browsers.
            </p>
            <span className={`text-[10px] font-mono block ${permissions.overlay ? 'text-[#f87171]' : 'text-[#f87171] font-bold'}`}>
              {permissions.overlay ? 'SYSTEM_ALERT_WINDOW: GRANTED' : 'TAP TO OPEN SETTINGS'}
            </span>
          </div>

          {/* Accessibility Service */}
          <div 
            onClick={onRequestAccessibilityPermission}
            className={`p-3 bg-[#161b22] rounded-xl border space-y-1.5 transition-all cursor-pointer hover:border-[#ef4444] ${
              permissions.accessibility ? 'border-[#30363d] border-l-2 border-l-[#ef4444]' : 'border-[#ef4444] bg-[#1c1114]'
            }`}
          >
            <div className="flex items-center justify-between">
              <span className="text-xs font-semibold text-[#e1e4e8]">Accessibility Context</span>
              {permissions.accessibility ? (
                <CheckCircle2 className="w-3.5 h-3.5 text-[#ef4444]" />
              ) : (
                <span className="text-[10px] font-bold text-white bg-[#dc2626] px-1.5 py-0.5 rounded">ENABLE</span>
              )}
            </div>
            <p className="text-[11px] text-[#8b949e] leading-snug">
              Reads chat message text in foreground apps to generate instantaneous replies.
            </p>
            <span className={`text-[10px] font-mono block ${permissions.accessibility ? 'text-[#f87171]' : 'text-[#f87171] font-bold'}`}>
              {permissions.accessibility ? 'BIND_ACCESSIBILITY_SERVICE: ACTIVE' : 'TAP TO ENABLE IN ACCESSIBILITY'}
            </span>
          </div>

          {/* Foreground Daemon & Notifications */}
          <div 
            onClick={onRequestNotificationPermission}
            className={`p-3 bg-[#161b22] rounded-xl border space-y-1.5 transition-all cursor-pointer hover:border-[#ef4444] ${
              permissions.notifications ? 'border-[#30363d] border-l-2 border-l-[#ef4444]' : 'border-[#ef4444] bg-[#1c1114]'
            }`}
          >
            <div className="flex items-center justify-between">
              <span className="text-xs font-semibold text-[#e1e4e8]">Daemon Notification</span>
              {permissions.notifications ? (
                <CheckCircle2 className="w-3.5 h-3.5 text-[#ef4444]" />
              ) : (
                <span className="text-[10px] font-bold text-white bg-[#dc2626] px-1.5 py-0.5 rounded">ALLOW</span>
              )}
            </div>
            <p className="text-[11px] text-[#8b949e] leading-snug">
              Prevents Android OS from killing the floating overlay while multitasking.
            </p>
            <span className={`text-[10px] font-mono block ${permissions.notifications ? 'text-[#f87171]' : 'text-[#f87171] font-bold'}`}>
              {permissions.notifications ? 'FOREGROUND_SERVICE: ACTIVE' : 'TAP TO ALLOW NOTIFICATIONS'}
            </span>
          </div>
        </div>
      </div>

      {/* Quick Access Action Hub */}
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
        {/* Test in Simulator Card */}
        <div 
          onClick={() => onNavigateTab('simulator')}
          className="p-4 rounded-xl bg-[#0d1117] hover:bg-[#161b22] border border-[#30363d] hover:border-[#ef4444] transition-all cursor-pointer group space-y-2"
        >
          <div className="flex items-center justify-between">
            <div className="w-8 h-8 rounded-lg bg-[#21262d] border border-[#30363d] flex items-center justify-center text-[#ef4444]">
              <Smartphone className="w-4 h-4" />
            </div>
            <ArrowRight className="w-4 h-4 text-[#6e7681] group-hover:text-[#ef4444] group-hover:translate-x-1 transition-all" />
          </div>
          <div>
            <h3 className="text-xs font-bold text-[#e1e4e8] group-hover:text-[#ef4444] transition-colors">
              Interactive Phone Simulator
            </h3>
            <p className="text-[11px] text-[#8b949e] mt-0.5 leading-snug">
              Test floating replies over WhatsApp, Reddit, Discord, and custom questions with automatic generation in real time.
            </p>
          </div>
        </div>

        {/* Auto-Generation & Reply Settings */}
        <div 
          onClick={() => onNavigateTab('reply-settings')}
          className="p-4 rounded-xl bg-[#0d1117] hover:bg-[#161b22] border border-[#30363d] hover:border-[#ef4444] transition-all cursor-pointer group space-y-2"
        >
          <div className="flex items-center justify-between">
            <div className="w-8 h-8 rounded-lg bg-[#21262d] border border-[#30363d] flex items-center justify-center text-[#ef4444]">
              <Zap className="w-4 h-4" />
            </div>
            <ArrowRight className="w-4 h-4 text-[#6e7681] group-hover:text-[#ef4444] group-hover:translate-x-1 transition-all" />
          </div>
          <div>
            <h3 className="text-xs font-bold text-[#e1e4e8] group-hover:text-[#ef4444] transition-colors">
              Autonomous Intelligence Engine
            </h3>
            <p className="text-[11px] text-[#8b949e] mt-0.5 leading-snug">
              Configure hands-free response triggers, debate/humor tones, and context comprehension.
            </p>
          </div>
        </div>
      </div>
    </div>
  );
};
