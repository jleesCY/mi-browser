/** @type {import('next').NextConfig} */
const isProd = process.env.NODE_ENV === "production";
const repoName = process.env.GITHUB_REPOSITORY
  ? process.env.GITHUB_REPOSITORY.split("/")[1]
  : "mi-browser";

const nextConfig = {
  output: "export",
  basePath: isProd ? `/${repoName}` : "",
  trailingSlash: true,
  images: {
    unoptimized: true,
  },
  env: {
    NEXT_PUBLIC_REPO_NAME: repoName,
  },
};

export default nextConfig;
