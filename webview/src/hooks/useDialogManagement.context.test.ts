import { act, renderHook } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { useDialogManagement } from './useDialogManagement';

const sendBridgeEvent = vi.fn();

vi.mock('../utils/bridge', () => ({
  sendBridgeEvent: (...args: unknown[]) => sendBridgeEvent(...args),
}));

const t = ((key: string) => key) as any;

describe('useDialogManagement - context usage requestId isolation', () => {
  beforeEach(() => {
    sendBridgeEvent.mockReset();
  });

  it('openContextUsageDialog sets requestId and opens dialog', () => {
    const { result } = renderHook(() => useDialogManagement({ t }));

    act(() => {
      result.current.openContextUsageDialog('req-1', true);
    });

    expect(result.current.contextUsageDialogOpen).toBe(true);
    expect(result.current.contextUsageIsLoading).toBe(true);
    expect(result.current.contextUsageData).toBeNull();
  });

  it('updateContextUsageData accepts matching requestId', () => {
    const { result } = renderHook(() => useDialogManagement({ t }));
    const data = { totalTokens: 1000, maxTokens: 200000 } as any;

    act(() => {
      result.current.openContextUsageDialog('req-1', true);
    });

    let accepted: boolean;
    act(() => {
      accepted = result.current.updateContextUsageData('req-1', data);
    });

    expect(accepted!).toBe(true);
    expect(result.current.contextUsageIsLoading).toBe(false);
    expect(result.current.contextUsageData).toBe(data);
  });

  it('updateContextUsageData rejects stale requestId', () => {
    const { result } = renderHook(() => useDialogManagement({ t }));
    const data1 = { totalTokens: 1000 } as any;
    const data2 = { totalTokens: 2000 } as any;

    // Open with req-1
    act(() => {
      result.current.openContextUsageDialog('req-1', true);
    });

    // Open with req-2 (simulates a new request before the first completes)
    act(() => {
      result.current.openContextUsageDialog('req-2', true);
    });

    // Late response for req-1 should be rejected
    let accepted: boolean;
    act(() => {
      accepted = result.current.updateContextUsageData('req-1', data1);
    });
    expect(accepted!).toBe(false);
    expect(result.current.contextUsageData).toBeNull();

    // Response for req-2 should be accepted
    act(() => {
      accepted = result.current.updateContextUsageData('req-2', data2);
    });
    expect(accepted!).toBe(true);
    expect(result.current.contextUsageData).toBe(data2);
  });

  it('closeContextUsageDialog with no requestId closes current dialog', () => {
    const { result } = renderHook(() => useDialogManagement({ t }));

    act(() => {
      result.current.openContextUsageDialog('req-1', true);
    });
    expect(result.current.contextUsageDialogOpen).toBe(true);

    // Close without requestId (user clicks X button)
    let closed: boolean;
    act(() => {
      closed = result.current.closeContextUsageDialog();
    });

    expect(closed!).toBe(true);
    expect(result.current.contextUsageDialogOpen).toBe(false);
    expect(result.current.contextUsageData).toBeNull();
  });

  it('closeContextUsageDialog rejects stale requestId', () => {
    const { result } = renderHook(() => useDialogManagement({ t }));

    act(() => {
      result.current.openContextUsageDialog('req-1', true);
    });

    // Switch to req-2
    act(() => {
      result.current.openContextUsageDialog('req-2', true);
    });

    // Try to close with stale req-1
    let closed: boolean;
    act(() => {
      closed = result.current.closeContextUsageDialog('req-1');
    });

    expect(closed!).toBe(false);
    expect(result.current.contextUsageDialogOpen).toBe(true);

    // Close with correct req-2
    act(() => {
      closed = result.current.closeContextUsageDialog('req-2');
    });
    expect(closed!).toBe(true);
    expect(result.current.contextUsageDialogOpen).toBe(false);
  });

  it('closeContextUsageDialog with null requestId closes (force close)', () => {
    const { result } = renderHook(() => useDialogManagement({ t }));

    act(() => {
      result.current.openContextUsageDialog('req-1', true);
    });

    // null requestId means "close whatever is open"
    let closed: boolean;
    act(() => {
      closed = result.current.closeContextUsageDialog(null);
    });

    expect(closed!).toBe(true);
    expect(result.current.contextUsageDialogOpen).toBe(false);
  });
});

describe('useDialogManagement - dialog queue lifecycle', () => {
  beforeEach(() => {
    sendBridgeEvent.mockReset();
  });

  it('shows the next permission request after the current request is denied', () => {
    const { result } = renderHook(() => useDialogManagement({ t }));

    act(() => {
      result.current.openPermissionDialog({
        channelId: 'perm-1',
        toolName: 'Write',
        inputs: { file_path: '/tmp/a.txt' },
      });
      result.current.openPermissionDialog({
        channelId: 'perm-2',
        toolName: 'Write',
        inputs: { file_path: '/tmp/b.txt' },
      });
    });

    expect(result.current.permissionDialogOpen).toBe(true);
    expect(result.current.currentPermissionRequest?.channelId).toBe('perm-1');

    act(() => {
      result.current.handlePermissionSkip('perm-1');
    });

    expect(sendBridgeEvent).toHaveBeenCalledWith(
      'permission_decision',
      expect.stringContaining('"channelId":"perm-1"'),
    );
    expect(result.current.permissionDialogOpen).toBe(true);
    expect(result.current.currentPermissionRequest?.channelId).toBe('perm-2');
  });

  it('shows the next plan approval request after the current request is rejected', () => {
    const { result } = renderHook(() => useDialogManagement({ t }));

    act(() => {
      result.current.openPlanApprovalDialog({
        requestId: 'plan-1',
        toolName: 'ExitPlanMode',
        plan: 'first plan',
      });
      result.current.openPlanApprovalDialog({
        requestId: 'plan-2',
        toolName: 'ExitPlanMode',
        plan: 'second plan',
      });
    });

    expect(result.current.planApprovalDialogOpen).toBe(true);
    expect(result.current.currentPlanApprovalRequest?.requestId).toBe('plan-1');

    act(() => {
      result.current.handlePlanApprovalReject('plan-1');
    });

    expect(sendBridgeEvent).toHaveBeenCalledWith(
      'plan_approval_response',
      expect.stringContaining('"requestId":"plan-1"'),
    );
    expect(result.current.planApprovalDialogOpen).toBe(true);
    expect(result.current.currentPlanApprovalRequest?.requestId).toBe('plan-2');
  });

  it('shows the next ask-user request after the current request is cancelled', () => {
    const { result } = renderHook(() => useDialogManagement({ t }));

    act(() => {
      result.current.openAskUserQuestionDialog({
        requestId: 'ask-1',
        toolName: 'AskUserQuestion',
        questions: [],
      });
      result.current.openAskUserQuestionDialog({
        requestId: 'ask-2',
        toolName: 'AskUserQuestion',
        questions: [],
      });
    });

    expect(result.current.askUserQuestionDialogOpen).toBe(true);
    expect(result.current.currentAskUserQuestionRequest?.requestId).toBe('ask-1');

    act(() => {
      result.current.handleAskUserQuestionCancel('ask-1');
    });

    expect(sendBridgeEvent).toHaveBeenCalledWith(
      'ask_user_question_response',
      expect.stringContaining('"requestId":"ask-1"'),
    );
    expect(result.current.askUserQuestionDialogOpen).toBe(true);
    expect(result.current.currentAskUserQuestionRequest?.requestId).toBe('ask-2');
  });
});
