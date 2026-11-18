package com.example.archrules.spring.fixtures;

import java.util.List;

/** フィクスチャ用のサービス（インターフェースなので Bean としては検査されない） */
public interface OrderService {
    List<String> search(String user, String keyword);
}
