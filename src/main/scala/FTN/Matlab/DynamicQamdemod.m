function Demodulated = DynamicQamdemod(FDE)

    load('./data/bitAlloc.mat');

    % ?? 这里的代码无法处理比特分配有0的情况
    Demodulated = [];

    % for i = 1:length(bitAlloc)

    %     bitAllocated = bitAlloc(i); % 当前要处理的子载波(群)被分配的比特数

    %     if bitAllocated ~= 0
    %         QAM = reshape(FDE(i, :), [], 1); % 获取待解映射符号,并->串转换
    %         demodulated = Qamdemod(bitAllocated, QAM); % 依照分配比特数解映射
    %         Demodulated = [Demodulated, demodulated]; % 数据拼接方式和映射时一致,近邻数据被分配相同比特数
    %     end

    % end

    for i = 1:16
        symbolsOneCycle = reshape(FDE(:, i), 1, []);
        demodulatedOneCycle = SingleDynamicQamdemod(symbolsOneCycle, bitAlloc);
        Demodulated = [Demodulated, demodulatedOneCycle];
    end