import type {ChatMessage} from '@/types/chat'

function formatTime(ts: number): string {
    const d = new Date(ts)
    const pad = (n: number) => String(n).padStart(2, '0')
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function messageToMarkdown(msg: ChatMessage): string {
    switch (msg.role) {
        case 'user': {
            let md = `**用户** (${formatTime(msg.timestamp)})\n\n${msg.content}`
            if (msg.attachments?.length) {
                const list = msg.attachments.map(a => `- ${a.name} (${a.contentType})`).join('\n')
                md += `\n\n附件:\n${list}`
            }
            return md
        }
        case 'assistant':
            return `**助手** (${formatTime(msg.timestamp)})\n\n${msg.content}`
        case 'tool_call': {
            const args = Object.keys(msg.arguments).length > 0
                ? `\n\n**参数:**\n\`\`\`json\n${JSON.stringify(msg.arguments, null, 2)}\n\`\`\``
                : ''
            const result = msg.result ? `\n\n**结果:** ${msg.result}` : ''
            return `<details><summary>🔧 工具调用: ${msg.toolName}</summary>${args}${result}\n</details>`
        }
        case 'thinking':
            return `<details><summary>💭 思考过程</summary>\n\n${msg.content}\n</details>`
        case 'plan': {
            const result = msg.result ? `\n\n**结果:** ${msg.result}` : ''
            return `<details><summary>📋 计划: ${msg.toolName}</summary>${result}\n</details>`
        }
    }
}

export function exportToMarkdown(title: string, messages: ChatMessage[]): string {
    const header = `# ${title}\n\n> 导出时间: ${formatTime(Date.now())}\n`
    const body = messages.map(messageToMarkdown).join('\n\n---\n\n')
    return `${header}\n---\n\n${body}\n`
}
