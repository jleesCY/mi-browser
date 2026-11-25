// A phone showing one of the app's screenshots ([src], from public/), with a soft glow behind it in a theme colour.
import { getAssetPath } from "@/site";

export function DeviceMockup({ src, color }: { src: string, color?: string }) {
  const glowStyles: Record<string, string> = {
    blue: "bg-primary/20 shadow-primary/30",
    purple: "bg-tertiary/20 shadow-tertiary/30",
    pink: "bg-tertiary/20 shadow-tertiary/30",
    orange: "bg-secondary/20 shadow-secondary/30",
    cyan: "bg-secondary/20 shadow-secondary/30",
    green: "bg-secondary/20 shadow-secondary/30",
  };

  const glowClass = (color && glowStyles[color]) ? glowStyles[color].split(' ')[0] : 'bg-primary-container';

  return (
    <div className="relative group flex justify-center">
      <div className={`absolute -inset-10 ${glowClass} rounded-full blur-[80px] group-hover:opacity-100 transition-opacity opacity-50`} />
      <div className={`relative w-[220px] sm:w-[280px] bg-black rounded-[2rem] p-2 shadow-2xl border-[4px] border-[#1a1a1a] overflow-hidden transition-transform duration-500 group-hover:scale-105 group-hover:-rotate-1`}>
        <img
          src={getAssetPath(src)}
          alt="mi. App Screenshot"
          className="w-full h-auto block rounded-[1.4rem]"
        />
      </div>
    </div>
  );
}