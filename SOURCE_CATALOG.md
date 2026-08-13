# EmuHub remote source catalog

`/sources.json` is a lightweight index. It does **not** mirror or host drivers.
EmuHub reads the catalog, then fetches releases/components directly from the upstream URL configured for each source.

The default catalog URL is:

`https://raw.githubusercontent.com/Rodrig02005/EmuHub-APP/main/sources.json`

This means:

- A new release published by a configured GitHub Releases repository appears automatically on the next app launch/Refresh.
- A new version added to a configured `contents.json` appears automatically on the next app launch/Refresh.
- Adding/removing a provider or changing its filters only requires editing `sources.json`; it does not require rebuilding the APK.
- If the remote catalog is unavailable, the app falls back to a built-in copy of the default sources.

## Turnip source

```json
{
  "id": "example",
  "name": "Example",
  "apiUrl": "https://api.github.com/repos/owner/repo/releases",
  "description": "Short description shown in EmuHub.",
  "experimental": true,
  "enabled": true,
  "supportedSeries": ["6xx", "7xx", "8xx"],
  "filters": {
    "default": ["Turnip"]
  },
  "assetIncludes": {
    "8xx": ["A8xx"]
  },
  "assetExcludes": {
    "7xx": ["A8xx"]
  }
}
```

`filters` match release names, tags, or asset names. `assetIncludes` and `assetExcludes` filter the files inside matching releases. Keys can be `6xx`, `7xx`, `8xx`, or `default`.


## Qualcomm driver source

Qualcomm driver sources also use GitHub Releases, but are filtered separately from Turnip releases.

```json
{
  "id": "example-qualcomm",
  "name": "Example Qualcomm",
  "apiUrl": "https://api.github.com/repos/owner/repo/releases",
  "description": "Qualcomm driver source description.",
  "experimental": false,
  "enabled": true,
  "filters": ["Qualcomm Driver"]
}
```

## Component source

The component manifest must use the same array format already used by WinNative-style `contents.json` catalogs: `type`, `verName`, `verCode`, and `remoteUrl`.

```json
{
  "id": "example-components",
  "name": "Example Components",
  "manifestUrl": "https://example.com/contents.json",
  "description": "Component catalog description.",
  "experimental": true,
  "enabled": true
}
```

EmuHub discovers component types dynamically, so a new type in a compatible manifest can appear in the Download Hub without adding it to a hardcoded list first.

## Custom catalog

In **Settings > Sources**, paste any HTTP(S) catalog URL and press **Save**. Press **Default** to return to the EmuHub-managed catalog.
