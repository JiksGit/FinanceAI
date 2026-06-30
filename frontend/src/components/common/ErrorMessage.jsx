export default function ErrorMessage({ message }) {
  return (
    <div className="rounded-md bg-red-50 px-4 py-3 text-sm text-red-700">
      {message || '오류가 발생했습니다.'}
    </div>
  )
}
