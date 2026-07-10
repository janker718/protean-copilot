import { act, renderHook } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { useUsageTracking } from './useUsageTracking';

describe('useUsageTracking', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.spyOn(console, 'warn').mockImplementation(() => {});
  });

  it('allows the provider runtime to determine availability when status callback never arrives', () => {
    const { result } = renderHook(() => useUsageTracking());

    expect(result.current.sdkStatusLoaded).toBe(false);

    act(() => {
      vi.advanceTimersByTime(5000);
    });

    expect(result.current.sdkStatusLoaded).toBe(true);
    expect(result.current.isSdkInstalled('claude')).toBe(true);
    expect(console.warn).toHaveBeenCalledWith(
      '[Frontend] SDK status load timed out; allowing the provider runtime to determine availability',
    );
  });

  it('still blocks a provider explicitly reported as not installed', () => {
    const { result } = renderHook(() => useUsageTracking());

    act(() => {
      result.current.setSdkStatus({
        'claude-sdk': { status: 'not_installed' },
      });
      result.current.setSdkStatusLoaded(true);
    });

    expect(result.current.isSdkInstalled('claude')).toBe(false);
  });

  it('does not trigger timeout fallback after sdk status loads normally', () => {
    const { result } = renderHook(() => useUsageTracking());

    act(() => {
      result.current.setSdkStatus({
        'codex-sdk': { installed: true, status: 'installed' },
      });
      result.current.setSdkStatusLoaded(true);
    });

    act(() => {
      vi.advanceTimersByTime(5000);
    });

    expect(result.current.sdkStatusLoaded).toBe(true);
    expect(result.current.isSdkInstalled('codex')).toBe(true);
    expect(console.warn).not.toHaveBeenCalled();
  });
});
