package com.example.api_venda_concorrente.service;

import com.example.api_venda_concorrente.model.Produto;
import com.example.api_venda_concorrente.repository.ProdutoRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class ProdutoService {
    private final ProdutoRepository repository;

    public ProdutoService(ProdutoRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void realizarVenda(Long id, int quantidade) {
        Produto produto = repository.findByIdWithLock(id)
                .orElseThrow(() -> new IllegalArgumentException("Produto não encontrado"));

        if(produto.getQuantidadeEstoque() < quantidade) throw new IllegalStateException("Estoque insuficiente para a venda");
        produto.setQuantidadeEstoque(produto.getQuantidadeEstoque() - 1);
        repository.save(produto);
    }
}
