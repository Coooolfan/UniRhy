export const AiRequestFormat_CONSTANTS = [
    'OPENAI', 
    'GEMINI', 
    'CLAUDE', 
    'JINA'
] as const;
export type AiRequestFormat = typeof AiRequestFormat_CONSTANTS[number];
