import jsQR from 'jsqr';
import * as FileSystem from 'expo-file-system/legacy';
import { Buffer } from 'buffer';
import * as jpeg from 'jpeg-js';
import { PNG } from 'pngjs/browser';

export const detectQRPureJS = async (uri: string): Promise<string | null> => {
  try {
    // 1. Read file as base64
    const base64 = await FileSystem.readAsStringAsync(uri, {
      encoding: FileSystem.EncodingType.Base64,
    });
    
    // 2. Convert to Buffer
    const buffer = Buffer.from(base64, 'base64');
    
    let data: Uint8Array | null = null;
    let width = 0;
    let height = 0;

    // 3. Determine type and decode
    // Simple signature check: JPEG starts with FF D8, PNG with 89 50 4E 47
    if (buffer[0] === 0xff && buffer[1] === 0xd8) {
      const decoded = jpeg.decode(buffer, { useTArray: true });
      data = decoded.data;
      width = decoded.width;
      height = decoded.height;
    } else if (buffer[0] === 0x89 && buffer[1] === 0x50 && buffer[2] === 0x4e && buffer[3] === 0x47) {
       await new Promise<void>((resolve, reject) => {
           new PNG().parse(buffer, (err: any, parsed: any) => {
               if (err) {
                   reject(err);
                   return;
               }
               data = parsed.data;
               width = parsed.width;
               height = parsed.height;
               resolve();
           });
       });
    } else {
        // Fallback: try JPEG decoding anyway if extension implies it, or just fail
        // For now, assume if not PNG signature, try JPEG
        try {
            const decoded = jpeg.decode(buffer, { useTArray: true });
            data = decoded.data;
            width = decoded.width;
            height = decoded.height;
        } catch {}
    }

    if (!data || width === 0 || height === 0) {
        console.log("Failed to decode image data");
        return null;
    }

    // 4. Scan with jsQR
    // jsQR expects a Uint8ClampedArray of RGBA pixel data
    const clamped = new Uint8ClampedArray(data);
    const code = jsQR(clamped, width, height);

    if (code) {
      return code.data;
    }
  } catch (e) {
    console.log("Pure JS QR Scan Error:", e);
  }
  return null;
};
