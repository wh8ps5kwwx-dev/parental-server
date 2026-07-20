import 'dart:convert';

import 'package:http/http.dart' as http;

import '../../config/server_config.dart';

/// عميل HTTP مشترك — نفس ترويسات NetworkModule.kt
class ApiClient {
  ApiClient({http.Client? client}) : _client = client ?? http.Client();

  final http.Client _client;

  Map<String, String> get _headers => {
        'Content-Type': 'application/json; charset=utf-8',
        'Accept': 'application/json',
        ServerConfig.apiKeyHeader: ServerConfig.apiKey,
      };

  Uri _root(String path, [Map<String, String>? query]) {
    final p = path.startsWith('/') ? path.substring(1) : path;
    return Uri.parse('${ServerConfig.rootUrl}$p').replace(queryParameters: query);
  }

  Uri apiV1(String path) {
    final p = path.startsWith('/') ? path.substring(1) : path;
    return Uri.parse('${ServerConfig.baseApiUrl}$p');
  }

  Future<Map<String, dynamic>> postRoot(
    String path,
    Map<String, dynamic> body,
  ) async {
    final res = await _client.post(
      _root(path),
      headers: _headers,
      body: jsonEncode(body),
    );
    return _decode(res);
  }

  Future<Map<String, dynamic>> getRoot(
    String path, {
    Map<String, String>? query,
  }) async {
    final res = await _client.get(_root(path, query), headers: _headers);
    return _decode(res);
  }

  Future<dynamic> getRootRaw(
    String path, {
    Map<String, String>? query,
  }) async {
    final res = await _client.get(_root(path, query), headers: _headers);
    if (res.body.isEmpty) return null;
    return jsonDecode(utf8.decode(res.bodyBytes));
  }

  Map<String, dynamic> _decode(http.Response res) {
    final text = utf8.decode(res.bodyBytes);
    if (text.isEmpty) {
      return {'status': 'error', 'message': 'استجابة فارغة (${res.statusCode})'};
    }
    final decoded = jsonDecode(text);
    if (decoded is Map<String, dynamic>) return decoded;
    if (decoded is Map) return Map<String, dynamic>.from(decoded);
    return {'status': 'error', 'message': 'صيغة غير متوقعة', 'raw': decoded};
  }

  void close() => _client.close();
}
