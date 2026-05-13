export declare const STATIC_URL_PREFIX: string

export declare function getStaticContentType(filePath: string): string

export declare function getStaticResponseHeaders(filePath: string): {
  'Content-Type': string
  'X-Content-Type-Options': 'nosniff'
}
