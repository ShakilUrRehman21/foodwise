import Link from "next/link";
import { Button } from "@/components/ui/button";

export default function NotFound() {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-3 bg-[#F8F7F2] p-6 text-center">
      <p className="text-5xl">🥡</p>
      <h1 className="text-xl font-bold text-[#26372C]">This dabba is empty</h1>
      <p className="text-sm text-[#788278]">The page you&apos;re looking for doesn&apos;t exist.</p>
      <Button asChild>
        <Link href="/dashboard">Back to your kitchen</Link>
      </Button>
    </div>
  );
}
