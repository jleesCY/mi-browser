/** @type {import('next').NextConfig} */
const isProd = process.env.NODE_ENV === 'production';

const nextConfig = {
  output: 'export',
  images: {
    unoptimized: true,
  },
  // Only use basePath in production (GitHub Pages)
  basePath: isProd ? '/my-browser' : '', 
};

module.exports = nextConfig;
