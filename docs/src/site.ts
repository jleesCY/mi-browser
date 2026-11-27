// What every page of the site shares: where it's served from (GitHub Pages under /<repo> in production) and the repo.

/** [path] (from public/) under the site's base path. */
export const getAssetPath = (path: string) =>
  `${process.env.NODE_ENV === "production" ? `/${process.env.NEXT_PUBLIC_REPO_NAME || "mi-browser"}` : ""}${path}`;

export const GITHUB_URL = "https://github.com/mi-app-hub/mi-browser";

/**
 * The other mi. apps (mi-brand's family table: every one but this, released or not), each linking to its own site at
 * mi-app-hub.github.io/mi-<slug>, the family's pattern (there before some of them go up). On the home page and in the
 * footer of every page.
 */
export const MI_APPS = [
  { name: "mi. Notes", line: "Notes in folders on one calm page", icon: "/family/notes.png", slug: "notes" },
  { name: "mi. Budget", line: "Your purchases, budgets and subscriptions, private on your phone", icon: "/family/budget.png", slug: "budget" },
  { name: "mi. Edits", line: "A simple, private video and audio editor", icon: "/family/edits.png", slug: "edits" },
  { name: "mi. Sports", line: "Live scores for the teams, players and leagues you follow", icon: "/family/sports.png", slug: "sports" },
  { name: "mi. Weather", line: "Every detail of the weather where you are, all inside the app", icon: "/family/weather.png", slug: "weather" },
].map((a) => ({ ...a, href: `https://mi-app-hub.github.io/mi-${a.slug}/` }));
