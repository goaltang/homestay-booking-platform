import request from "@/utils/request";

export interface AgentChatRequest {
  question: string;
  conversationId?: string;
  orderId?: number;
  homestayId?: number;
}

export interface AgentPendingAction {
  action: string;
  orderId: number;
  reason: string;
  summary: string;
}

export interface AgentChatResponse {
  answer: string;
  handoffToHuman: boolean;
  toolUsed: string | null;
  conversationId: string;
  pendingAction: AgentPendingAction | null;
}

export function chatWithAgent(req: AgentChatRequest): Promise<AgentChatResponse> {
  return request
    .post<{ success: boolean; code: number; message: string; data: AgentChatResponse }>(
      "/api/support/agent/chat",
      req,
    )
    .then((response) => response.data.data);
}

export function confirmAgentAction(pending: AgentPendingAction): Promise<AgentChatResponse> {
  return request
    .post<{ success: boolean; code: number; message: string; data: AgentChatResponse }>(
      "/api/support/agent/confirm",
      pending,
    )
    .then((response) => response.data.data);
}
