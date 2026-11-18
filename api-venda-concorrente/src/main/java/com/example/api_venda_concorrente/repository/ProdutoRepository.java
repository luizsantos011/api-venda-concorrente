package com.example.api_venda_concorrente.repository;

import com.example.api_venda_concorrente.model.Produto;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProdutoRepository extends JpaRepository<Produto, Long> {
}
