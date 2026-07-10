import { useCallback, useEffect, useState } from 'react';

const PROVIDER_TO_SDK: Record<string, string> = {
  claude: 'claude-sdk',
  anthropic: 'claude-sdk',
  bedrock: 'claude-sdk',
  codex: 'codex-sdk',
  openai: 'codex-sdk',
};

type SdkStatus = Record<string, { installed?: boolean; status?: string }>;
const SDK_STATUS_LOAD_TIMEOUT_MS = 5000;

/**
 * Usage % / token counters and SDK install status. `isSdkInstalled(providerId)`
 * is exposed as a stable callback for callers that need to gate UI on SDK
 * availability. The sdkStatusLoaded flag must be true before queries return
 * meaningful results.
 */
export function useUsageTracking() {
  const [usagePercentage, setUsagePercentage] = useState(0);
  const [usageUsedTokens, setUsageUsedTokens] = useState<number | undefined>(undefined);
  const [usageMaxTokens, setUsageMaxTokens] = useState<number | undefined>(undefined);
  const [sdkStatus, setSdkStatus] = useState<SdkStatus>({});
  const [sdkStatusLoaded, setSdkStatusLoaded] = useState(false);

  useEffect(() => {
    if (sdkStatusLoaded) return undefined;

    const timeoutId = window.setTimeout(() => {
      console.warn('[Frontend] SDK status load timed out; allowing the provider runtime to determine availability');
      setSdkStatusLoaded(true);
    }, SDK_STATUS_LOAD_TIMEOUT_MS);

    return () => window.clearTimeout(timeoutId);
  }, [sdkStatusLoaded]);

  const isSdkInstalled = useCallback(
    (providerId: string): boolean => {
      if (!sdkStatusLoaded) return false;
      const sdkId = PROVIDER_TO_SDK[providerId] || 'claude-sdk';
      const status = sdkStatus[sdkId];

      // A missing status is an unavailable status check, not evidence that the
      // SDK is absent. Let the provider bridge report the concrete runtime
      // error instead of blocking a working managed SDK after the timeout.
      if (!status) return true;

      return status?.status === 'installed' || status?.installed === true;
    },
    [sdkStatusLoaded, sdkStatus],
  );

  return {
    usagePercentage,
    setUsagePercentage,
    usageUsedTokens,
    setUsageUsedTokens,
    usageMaxTokens,
    setUsageMaxTokens,
    sdkStatus,
    setSdkStatus,
    sdkStatusLoaded,
    setSdkStatusLoaded,
    isSdkInstalled,
  };
}

export type UseUsageTrackingReturn = ReturnType<typeof useUsageTracking>;
