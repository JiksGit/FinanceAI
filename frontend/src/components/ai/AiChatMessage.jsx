export default function AiChatMessage({ role, content }) {
  const isUser = role === 'user'

  return (
    <div className={`flex ${isUser ? 'justify-end' : 'justify-start'}`}>
      <div
        className={`max-w-[75%] whitespace-pre-wrap rounded-lg px-4 py-2 text-sm ${
          isUser ? 'bg-indigo-600 text-white' : 'bg-slate-100 text-slate-800'
        }`}
      >
        {content}
      </div>
    </div>
  )
}
