"use client";

import { Camera, ChefHat, Leaf, ShoppingCart, Soup, TimerReset } from "lucide-react";
import Link from "next/link";
import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { useAppAuth } from "@/lib/auth";
import { formatInr } from "@/lib/format";

const FEATURES = [
  { icon: Camera, title: "Scan your bill", text: "Snap a grocery receipt and review the items before they're added. No typing lists." },
  { icon: TimerReset, title: "Know what's expiring", text: "A simple fresh / use soon / today view and one calm morning reminder." },
  { icon: ChefHat, title: "Cook what you have", text: "80+ Indian recipes ranked by what's in your kitchen and what expires first." },
  { icon: Soup, title: "Track leftovers", text: "Log the dal and rice dabbas, and get ideas like dal paratha or lemon rice." },
  { icon: ShoppingCart, title: "Shop smarter", text: "A shared list that knows what you already have and what's running low." },
  { icon: Leaf, title: "See the rupees", text: "Monthly spend, waste and money rescued — by category." },
];

export default function LandingPage() {
  const { isSignedIn, isLoaded } = useAppAuth();
  const [spend, setSpend] = useState(8000);
  // Illustrative only: the user picks the share of groceries they think gets thrown away.
  const [wastePct, setWastePct] = useState(15);
  const monthlyWaste = Math.round((spend * wastePct) / 100);

  return (
    <div className="min-h-screen bg-[#F8F7F2]">
      <header className="mx-auto flex max-w-6xl items-center justify-between px-4 py-4 sm:px-6">
        <span className="flex items-center gap-2">
          <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-[#668568] text-white">
            <Leaf className="h-5 w-5" />
          </span>
          <span className="text-lg font-bold text-[#26372C]">FoodWise</span>
        </span>
        {isLoaded && (
          <Button asChild variant={isSignedIn ? "default" : "secondary"} size="sm">
            <Link href={isSignedIn ? "/dashboard" : "/sign-in"}>{isSignedIn ? "Open app" : "Sign in"}</Link>
          </Button>
        )}
      </header>

      <section className="mx-auto max-w-6xl px-4 pb-12 pt-8 sm:px-6 sm:pt-16">
        <div className="grid items-center gap-10 lg:grid-cols-2">
          <div>
            <h1 className="text-4xl font-bold leading-tight tracking-tight text-[#26372C] sm:text-5xl">
              Know what you have.
              <br />
              <span className="text-[#668568]">Waste less.</span>
            </h1>
            <p className="mt-4 max-w-md text-lg text-[#5E6A5F]">
              A calm kitchen companion for Indian homes: track groceries and leftovers, cook what&apos;s about to expire, and stop buying what you already have.
            </p>
            <div className="mt-6 flex flex-wrap gap-3">
              <Button asChild size="lg">
                <Link href={isSignedIn ? "/dashboard" : "/sign-up"}>{isSignedIn ? "Go to your kitchen" : "Get started — it's free"}</Link>
              </Button>
            </div>
          </div>
          <Card className="p-6">
            <p className="text-sm font-semibold text-[#26372C]">How much could you be throwing away?</p>
            <label className="mt-4 block text-xs text-[#788278]">
              Monthly grocery spend: <strong className="text-[#26372C]">{formatInr(spend)}</strong>
              <input type="range" min={2000} max={30000} step={500} value={spend} onChange={(e) => setSpend(Number(e.target.value))} className="mt-2 w-full accent-[#668568]" />
            </label>
            <label className="mt-4 block text-xs text-[#788278]">
              Share you think goes to waste: <strong className="text-[#26372C]">{wastePct}%</strong>
              <input type="range" min={0} max={40} value={wastePct} onChange={(e) => setWastePct(Number(e.target.value))} className="mt-2 w-full accent-[#668568]" />
            </label>
            <div className="mt-5 rounded-xl bg-[#FCEDE8] p-4">
              <p className="text-sm text-[#C04334]">That&apos;s about</p>
              <p className="text-3xl font-bold text-[#C04334]">
                {formatInr(monthlyWaste)}
                <span className="text-base font-medium"> / month</span>
              </p>
              <p className="mt-1 text-xs text-[#C04334]">{formatInr(monthlyWaste * 12)} a year, based on your own estimate.</p>
            </div>
          </Card>
        </div>
      </section>

      <section className="mx-auto max-w-6xl px-4 pb-16 sm:px-6">
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {FEATURES.map((f) => (
            <Card key={f.title} className="p-5">
              <f.icon className="h-6 w-6 text-[#668568]" />
              <h3 className="mt-3 font-semibold text-[#26372C]">{f.title}</h3>
              <p className="mt-1 text-sm text-[#788278]">{f.text}</p>
            </Card>
          ))}
        </div>
      </section>

      <footer className="border-t border-[#E6E3DA] py-6 text-center text-xs text-[#788278]">
        FoodWise · Your data stays private. Receipt scanning uses on-server OCR — no third-party AI.
      </footer>
    </div>
  );
}
