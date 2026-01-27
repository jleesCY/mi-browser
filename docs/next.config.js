/** @type {import('next').NextConfig} */
const nextConfig = {
  output: 'export',
  images: {
    unoptimized: true,
  },
  // Set the base path for GitHub Pages
  basePath: '/my-browser', 
};

module.exports = nextConfig;