// What every page of the site shares: where it's served from (GitHub Pages under /<repo> in production) and the repo.

/** [path] (from public/) under the site's base path. */
export const getAssetPath = (path: string) =>
  `${process.env.NODE_ENV === "production" ? `/${process.env.NEXT_PUBLIC_REPO_NAME || "mi-browser"}` : ""}${path}`;

export const GITHUB_URL = "https://github.com/jleescy/mi-browser";
