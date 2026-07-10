/**
 * agentCallbacks.ts
 *
 * Registers window bridge callbacks for agent management and selection context:
 * addSelectionInfo, addCodeSnippet, clearSelectionInfo,
 * updateSelectedAgent, onSelectedAgentReceived, onSelectedAgentChanged.
 */

import type { UseWindowCallbacksOptions } from '../../useWindowCallbacks';

export function registerAgentAndSelectionCallbacks(options: UseWindowCallbacksOptions): void {
  const {
    setContextInfo,
    setSelectedAgent,
  } = options;

  window.addSelectionInfo = (selectionInfo) => {
    if (selectionInfo) {
      const match = selectionInfo.match(/^@([^#]+)(?:#L(\d+)(?:-(\d+))?)?$/);
      if (match) {
        const file = match[1];
        const startLine = match[2] ? parseInt(match[2], 10) : undefined;
        const endLine =
          match[3] ? parseInt(match[3], 10) : startLine !== undefined ? startLine : undefined;
        setContextInfo({
          file,
          startLine,
          endLine,
          raw: selectionInfo,
        });
      }
    }
  };

  window.addCodeSnippet = (selectionInfo) => {
    if (selectionInfo && window.insertCodeSnippetAtCursor) {
      window.insertCodeSnippetAtCursor(selectionInfo);
    }
  };

  window.clearSelectionInfo = () => {
    setContextInfo(null);
  };

  const applySelectedAgent = (json: string) => {
    try {
      if (!json || json === 'null' || json === '{}') {
        setSelectedAgent(null);
        return;
      }
      const data = JSON.parse(json);
      const agentFromNewShape = data?.agent;
      const agentFromLegacyShape = data;

      const agentData = agentFromNewShape?.id
        ? agentFromNewShape
        : agentFromLegacyShape?.id
          ? agentFromLegacyShape
          : null;
      if (!agentData) {
        setSelectedAgent(null);
        return;
      }

      setSelectedAgent({
        id: agentData.id,
        name: agentData.name || '',
        prompt: agentData.prompt,
      });
    } catch (error) {
      console.error('[Frontend] Failed to parse selected agent:', error);
      setSelectedAgent(null);
    }
  };

  // updateSelectedAgent is the current Java bridge callback. Keep the former
  // name as an alias because older bridge implementations can still emit it.
  window.updateSelectedAgent = applySelectedAgent;
  window.onSelectedAgentReceived = applySelectedAgent;

  if (window.__pendingSelectedAgent !== undefined) {
    const pending = window.__pendingSelectedAgent;
    delete window.__pendingSelectedAgent;
    applySelectedAgent(pending);
  }

  window.onSelectedAgentChanged = (json) => {
    try {
      if (!json || json === 'null' || json === '{}') {
        setSelectedAgent(null);
        return;
      }

      const data = JSON.parse(json);
      if (data?.success === false) {
        return;
      }

      const agentData = data?.agent;
      if (!agentData || !agentData.id) {
        setSelectedAgent(null);
        return;
      }

      setSelectedAgent({
        id: agentData.id,
        name: agentData.name || '',
        prompt: agentData.prompt,
      });
    } catch (error) {
      console.error('[Frontend] Failed to parse selected agent changed:', error);
    }
  };
}
